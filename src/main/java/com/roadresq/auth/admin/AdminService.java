package com.roadresq.auth.admin;


import com.roadresq.auth.admin.request.*;
import com.roadresq.auth.admin.response.AdminAuthResponse;
import com.roadresq.auth.admin.response.AdminForgotPasswordResponse;
import com.roadresq.auth.admin.response.AdminResponse;
import com.roadresq.auth.adminActivity.AdminActivityService;
import com.roadresq.auth.platformMemberShip.PlatformMemberShipRepository;
import com.roadresq.auth.userauthmgt.user.User;
import com.roadresq.auth.userauthmgt.user.UserService;
import com.roadresq.auth.utils.PaginationService;
import com.roadresq.auth.utils.appsecurity.JwtService;
import com.roadresq.auth.utils.exception.*;
import com.roadresq.auth.utils.fileupload.FileUploadService;
import com.roadresq.auth.vendor.VendorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import com.roadresq.auth.platformMemberShip.PlatformMemberShip;
import com.roadresq.auth.userauthmgt.user.UserType;
import com.roadresq.auth.utils.ServicePlatformType;
import com.roadresq.auth.utils.PaginatedData;
import com.roadresq.auth.admin.request.AdminAccessRequest;
import com.roadresq.auth.userauthmgt.user.request.PasswordChangeRequest;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Log4j2
@RequiredArgsConstructor
public class AdminService {

    private final AdminActivityService adminActivityService;
    private final MongoTemplate mongoTemplate;
    private final PlatformMemberShipRepository membershipRepository;
    private final VendorService vendorService;
    private final PaginationService paginationService;
    private final AdminRepository adminRepository;
    private final UserService userService;
    private final JwtService jwtService;
    private final FileUploadService fileUploadService;

    public AdminResponse createFirstAdmin(AdminCreateRequest request) {
        validateCreateRequest(request);
        if (adminRepository.countByDeleted(false) > 0) {
            throw new InvalidOperationException("First admin already exists");
        }
        if (request.firstName().trim().length() > 80 || request.lastName().trim().length() > 80 || request.emailAddress().length() > 254
                || !request.emailAddress().trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new InvalidInputException("Valid names and email are required");
        User user = userService.createAdminUserFromAdminModule(
                clean(request.firstName()),
                clean(request.lastName()),
                cleanEmail(request.emailAddress()),
                request.password(),
                clean(request.phoneNumber()),
                clean(request.profilePictureUrl()),
                clean(request.profileColourCode()),
                request.gender(),
                true
        );

        Admin admin = Admin.builder()
                .userId(user.getId())
                .active(true)
                .deleted(false)
                .lastLoginAt(null)
                .deletedAt(null)
                .build();

        Admin savedAdmin = adminRepository.save(admin);
        return mapToResponse(savedAdmin, user);
    }

    @Transactional
    public AdminResponse createAdmin(AdminCreateRequest request) {
        requireSuperAdmin();
        validateCreateRequest(request);

        if (request.firstName().trim().length() > 80 || request.lastName().trim().length() > 80 || request.emailAddress().length() > 254
                || !request.emailAddress().trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new InvalidInputException("Valid names and email are required");
        User user = userService.createAdminUserFromAdminModule(
                clean(request.firstName()),
                clean(request.lastName()),
                cleanEmail(request.emailAddress()),
                request.password(),
                clean(request.phoneNumber()),
                clean(request.profilePictureUrl()),
                clean(request.profileColourCode()),
                request.gender(),
                true
        );

        if (adminRepository.existsByUserId(user.getId())) {
            throw new ResourceExistsException("User is already an admin");
        }

        Admin admin = Admin.builder()
                .userId(user.getId())
                .active(true)
                .deleted(false)
                .lastLoginAt(null)
                .deletedAt(null)
                .build();

        Admin savedAdmin = adminRepository.save(admin);
        adminActivityService.record("ADMIN_CREATED", savedAdmin.getId(), null, null, "ACTIVE");
        return mapToResponse(savedAdmin, user);
    }

    public AdminAuthResponse login(AdminLoginRequest request) {
        if (request == null || isBlank(request.emailAddress()) || isBlank(request.password())) {
            throw new InvalidInputException("Email address and password are required");
        }
        User user = userService.authenticateAdminUserFromAdminModule(
                cleanEmail(request.emailAddress()),
                request.password()
        );
        Admin admin = adminRepository.findByUserIdAndDeleted(user.getId(), false)
                .orElseThrow(() -> new UnauthorizedException("Admin account not found"));

        if (!admin.isActive()) {
            throw new UnauthorizedException("Admin account is inactive");
        }
        admin.setLastLoginAt(LocalDateTime.now());
        Admin savedAdmin = adminRepository.save(admin);
        Map<String, Object> claims = buildJwtClaims(savedAdmin, user);
        String accessToken = jwtService.generateToken(user.getEmailAddress(), claims);
        String refreshToken = jwtService.generateRefreshToken(user.getEmailAddress(), claims);

        return new AdminAuthResponse(
                accessToken,
                refreshToken,
                mapToResponse(savedAdmin, user)
        );
    }

    public AdminResponse getCurrentAdminProfile() {
        String emailAddress = getCurrentPrincipalEmail();

        User user = userService.getAdminUserByEmailFromAdminModule(emailAddress);

        Admin admin = adminRepository.findByUserIdAndDeleted(user.getId(), false)
                .orElseThrow(() -> new ResourceNotFoundException("Admin profile not found"));

        if (!admin.isActive() || !user.isActive() || !user.isVerified() || user.isDeleted()) {
            throw new UnauthorizedException("Admin account is inactive or unavailable");
        }
        return mapToResponse(admin, user);
    }

    public List<AdminResponse> getAdmins() {
        requireSuperAdmin();
        return adminRepository.findAllByDeleted(false)
                .stream()
                .map(admin -> mapToResponse(admin, userService.getAdminUserByIdFromAdminModule(admin.getUserId())))
                .toList();
    }

    public AdminResponse getAdminById(String adminId) {
        requireSuperAdmin();
        Admin admin = findAdminById(adminId);
        User user = userService.getAdminUserByIdFromAdminModule(admin.getUserId());

        return mapToResponse(admin, user);
    }

    @Transactional
    public AdminResponse updateAdmin(String adminId, AdminUpdateRequest request) {
        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        if ((request.firstName() != null && request.firstName().isBlank()) || (request.lastName() != null && request.lastName().isBlank())) {
            throw new InvalidInputException("Names cannot be blank");
        }
        Admin admin = findAdminById(adminId);
        AdminResponse actor = getCurrentAdminProfile();
        if (!actor.adminId().equals(adminId)) {
            requireSuperAdmin();
            requireManageable(admin);
        }
        User oldUser = userService.getAdminUserByIdFromAdminModule(admin.getUserId());

        String oldProfilePictureUrl = oldUser.getProfilePictureUrl();

        User updatedUser = userService.updateAdminUserFromAdminModule(admin.getUserId(), request);


        admin.setUpdatedAt(LocalDateTime.now());

        Admin savedAdmin = adminRepository.save(admin);

        fileUploadService.deleteOldFileIfChanged(oldProfilePictureUrl, updatedUser.getProfilePictureUrl());
        adminRepository.save(admin);
        userService.save(updatedUser);
        adminActivityService.record("ADMIN_PROFILE_UPDATED", adminId, null, null, null);
        return mapToResponse(savedAdmin, updatedUser);
    }

    @Transactional
    public AdminResponse activateAdmin(String adminId) {
        requireSuperAdmin();
        Admin admin = findAdminById(adminId);
        requireManageable(admin);

        admin.setActive(true);
        admin.setUpdatedAt(LocalDateTime.now());

        Admin savedAdmin = adminRepository.save(admin);

        User user = userService.toggleAdminUserActiveStatusFromAdminModule(admin.getUserId(), true);


        adminActivityService.record("ADMIN_ACTIVATED", adminId, null, null, "True");
        return mapToResponse(savedAdmin, user);
    }

    @Transactional
    public AdminResponse deactivateAdmin(String adminId) {
        requireSuperAdmin();
        Admin admin = findAdminById(adminId);
        requireManageable(admin);
        admin.setActive(false);
        admin.setUpdatedAt(LocalDateTime.now());
        Admin savedAdmin = adminRepository.save(admin);
        User user = userService.toggleAdminUserActiveStatusFromAdminModule(admin.getUserId(), false);
        adminActivityService.record("ADMIN_DEACTIVATED", adminId, null, null, "False");
        return mapToResponse(savedAdmin, user);
    }

    @Transactional
    public void deleteAdmin(String adminId) {
        requireSuperAdmin();
        Admin admin = findAdminById(adminId);
        requireManageable(admin);
        User user = userService.getAdminUserByIdFromAdminModule(admin.getUserId());
        String profilePictureUrl = user.getProfilePictureUrl();
        admin.setDeleted(true);
        admin.setActive(false);
        admin.setDeletedAt(LocalDateTime.now());
        admin.setUpdatedAt(LocalDateTime.now());
        Admin savedAdmin = adminRepository.save(admin);
        userService.softDeleteAdminUserFromAdminModule(admin.getUserId());
        adminActivityService.record("ADMIN_DELETED", adminId, null, "ACTIVE", "DELETED");
        fileUploadService.deleteFileAsync(profilePictureUrl);
    }

    public long countAllAdmins() {
        return adminRepository.countByDeleted(false);
    }

    public long countActiveAdmins() {
        return adminRepository.countByDeletedAndActive(false, true);
    }

    public long countInactiveAdmins() {
        return adminRepository.countByDeletedAndActive(false, false);
    }

    private Admin findAdminById(String adminId) {
        if (isBlank(adminId)) {
            throw new ResourceNotFoundException("Admin not found");
        }

        return adminRepository.findByIdAndDeleted(adminId, false)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
    }

    private Map<String, Object> buildJwtClaims(Admin admin, User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("passwordVersion", passwordVersion(user));
        claims.put("adminId", admin.getId());
        claims.put("email", user.getEmailAddress());
        claims.put("userType", "ADMIN");
        claims.put("servicePlatformType", userService.getRequestedServicePlatformType().name());
        claims.put("userRole", admin.isSuperAdmin() ? "SUPER_ADMIN" : "ADMIN");
        claims.put("auth", admin.isSuperAdmin()
                ? List.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN") : List.of("ROLE_ADMIN"));
        return claims;
    }

    private AdminResponse mapToResponse(Admin admin, User user) {
        return new AdminResponse(
                admin.getId(),
                admin.getUserId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmailAddress(),
                user.getPhoneNumber(),
                user.getProfilePictureUrl(),
                user.getDateOfBirth(),
                user.getGender(),
                user.getAge(),
                user.getUserType(),
                admin.isActive(),
                user.isActive(),
                user.isVerified(),
                admin.getCreatedAt(),
                admin.getUpdatedAt(),
                admin.getLastLoginAt(),
                admin.isSuperAdmin()
        );
    }

    private void validateCreateRequest(AdminCreateRequest request) {
        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        if (isBlank(request.firstName())) {
            throw new InvalidInputException("First name is required");
        }

        if (isBlank(request.lastName())) {
            throw new InvalidInputException("Last name is required");
        }

        if (isBlank(request.emailAddress())) {
            throw new InvalidInputException("Email address is required");
        }

        if (isBlank(request.password())) {
            throw new InvalidInputException("Password is required");
        }

        if (request.password().length() < 12 || request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new InvalidInputException("Password must be at least 12 characters and at most 72 UTF-8 bytes");
        }
    }


    private String buildFullName(User user) {
        if (user == null) {
            return "Unknown admin";
        }

        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String lastName = user.getLastName() == null ? "" : user.getLastName().trim();

        String fullName = (firstName + " " + lastName).trim();

        return fullName.isBlank() ? user.getEmailAddress() : fullName;
    }

    private String getCurrentPrincipalEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal == null) {
            throw new UnauthorizedException("Authentication is required");
        }

        return principal.toString();
    }

    public AdminForgotPasswordResponse initiateAdminForgotPassword(AdminForgotPasswordRequest request) {
        if (request == null || isBlank(request.emailAddress())) {
            throw new InvalidInputException("Email address is required");
        }

        User user = userService.getAdminUserByEmailFromAdminModule(
                cleanEmail(request.emailAddress())
        );

        Admin admin = adminRepository.findByUserIdAndDeleted(user.getId(), false)
                .orElseThrow(() -> new ResourceNotFoundException("Admin account not found"));

        if (!admin.isActive() || !user.isActive()) {
            throw new InvalidOperationException("Admin account is not active");
        }

        if (!user.isVerified()) {
            throw new InvalidOperationException("Admin account is not verified");
        }

        Map<String, String> otp = userService.generatePasswordResetOtpForAdmin(user.getId());

        return new AdminForgotPasswordResponse(
                user.getId(),
                user.getEmailAddress(),
                otp.get("otp"),
                otp.get("expiresAt")
        );
    }

    @Transactional
    public void resetAdminPassword(AdminResetPasswordRequest request) {
        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        if (isBlank(request.emailAddress())) {
            throw new InvalidInputException("Email address is required");
        }

        if (isBlank(request.otp())) {
            throw new InvalidInputException("OTP is required");
        }

        if (isBlank(request.newPassword())) {
            throw new InvalidInputException("New password is required");
        }

        if (request.newPassword().length() < 6) {
            throw new InvalidInputException("Password must be at least 12 characters and at most 72 UTF-8 bytes");
        }

        User user = userService.getAdminUserByEmailFromAdminModule(
                cleanEmail(request.emailAddress())
        );

        Admin admin = adminRepository.findByUserIdAndDeleted(user.getId(), false)
                .orElseThrow(() -> new ResourceNotFoundException("Admin account not found"));

        if (!admin.isActive() || !user.isActive()) {
            throw new InvalidOperationException("Admin account is not active");
        }

        if (!user.isVerified()) {
            throw new InvalidOperationException("Admin account is not verified");
        }

        userService.resetAdminPasswordFromAdminModule(
                user.getId(),
                request.otp(),
                request.newPassword()
        );

    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private String cleanEmail(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public void requireSuperAdmin() {
        if (!getCurrentAdminProfile().superAdmin()) throw new UnauthorizedException("Super admin access is required");
    }

    private void requireManageable(Admin target) {
        if (target.isSuperAdmin() || target.getId().equals(getCurrentAdminProfile().adminId())) {
            throw new InvalidOperationException("Cannot manage your own access or a super admin through this endpoint");
        }
    }

    public String passwordVersion(User user) {
        return user.getPasswordChangedAt() == null ? "INITIAL" : user.getPasswordChangedAt().toString();
    }

    public boolean isCurrentToken(io.jsonwebtoken.Claims claims) {
        User user = userService.getAdminUserByEmailFromAdminModule(claims.getSubject());
        AdminResponse profile = getCurrentAdminProfile();
        Object authorities = claims.get("auth");
        return passwordVersion(user).equals(claims.get("passwordVersion", String.class))
                && authorities instanceof List<?> roles
                && profile.superAdmin() == roles.contains("ROLE_SUPER_ADMIN");
    }

     @Transactional
    public AdminResponse updateMyProfile(AdminUpdateRequest request) {
        return updateAdmin(getCurrentAdminProfile().adminId(), request);
    }

     @Transactional
    public void changeMyPassword(PasswordChangeRequest request) {
        AdminResponse actor = getCurrentAdminProfile();
        User user = userService.getAdminUserByIdFromAdminModule(actor.userId());
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        if (!encoder.matches(request.oldPassword(), user.getPassword()))
            throw new InvalidInputException("Current password is incorrect");
        if (request.newPassword().length() < 12 || request.newPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new InvalidInputException("Password must be at least 12 characters and at most 72 UTF-8 bytes");
        }
        if (encoder.matches(request.newPassword(), user.getPassword()))
            throw new InvalidInputException("Choose a different password");
        user.setPassword(encoder.encode(request.newPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        userService.updateUser(user);
        adminActivityService.record("ADMIN_PASSWORD_CHANGED", actor.adminId(), null, null, null);
    }


    public Map<String, Object> getPortalAnalytics(int days) {
        requireRoadPlatform();
        if (days != 7 && days != 30 && days != 90) throw new InvalidInputException("Choose 7, 30 or 90 days");
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        java.time.LocalDate first = today.minusDays(days - 1);
        java.util.Date from = java.util.Date.from(first.atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
        java.util.Date until = java.util.Date.from(today.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
        Map<String, Map<String, Long>> byRole = new HashMap<>();
        long active = 0;
        long inactive = 0;
        for (UserType role : List.of(UserType.CUSTOMER, UserType.VENDOR)) {
            List<Document> stages = new ArrayList<>();
            stages.add(new Document("$match", new Document("servicePlatformType", "ROAD_RESQ").append("userType", role.name())));
            stages.add(new Document("$set", new Document("userKey", new Document("$convert", new Document("input", "$userId").append("to", "objectId").append("onError", "$userId").append("onNull", "$userId")))));
            stages.add(new Document("$lookup", new Document("from", "user").append("localField", "userKey").append("foreignField", "_id").append("as", "user")));
            stages.add(new Document("$unwind", "$user"));
            stages.add(new Document("$lookup", new Document("from", role == UserType.VENDOR ? "vendors" : "customers").append("localField", "userId").append("foreignField", "userId").append("as", "profile")));
            stages.add(new Document("$unwind", "$profile"));
            Document match = new Document("user.isDeleted", new Document("$ne", true)).append("profile.deleted", new Document("$ne", true));
            stages.add(new Document("$match", match));

            Document validDate = new Document("profile.createdAt", new Document("$gte", from).append("$lt", until));
            Document day = new Document("$dateToString", new Document("format", "%Y-%m-%d").append("date", "$profile.createdAt").append("timezone", "UTC"));
            stages.add(new Document("$facet", new Document("growth", List.of(
                    new Document("$match", validDate),
                    new Document("$group", new Document("_id", day).append("count", new Document("$sum", 1)))))
                    .append("access", List.of(new Document("$group", new Document("_id", "$isActive").append("count", new Document("$sum", 1)))))));
            Document result = mongoTemplate.getCollection("platform_memberships").aggregate(stages).first();
            Map<String, Long> dates = new HashMap<>();
            if (result != null) {
                for (Document row : result.getList("growth", Document.class))
                    dates.put(row.getString("_id"), ((Number) row.get("count")).longValue());
                for (Document row : result.getList("access", Document.class)) {
                    if (Boolean.TRUE.equals(row.get("_id"))) active += ((Number) row.get("count")).longValue();
                    else inactive += ((Number) row.get("count")).longValue();
                }
            }
            byRole.put(role.name(), dates);
        }
        List<Map<String, Object>> growth = new ArrayList<>();
        for (java.time.LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
            growth.add(Map.of("date", day.toString(), "customers", byRole.get("CUSTOMER").getOrDefault(day.toString(), 0L),
                    "vendors", byRole.get("VENDOR").getOrDefault(day.toString(), 0L)));
        }
        log.info("Admin analytics retrieved for {} days", days);
        return Map.of("growth", growth, "activeMemberships", active, "inactiveMemberships", inactive,
                "from", first.toString(), "through", today.toString(), "timezone", "UTC");
    }

    public Map<String, Long> getPortalSummary() {
        requireRoadPlatform();
        Map<String, Long> summary = new HashMap<>();
        summary.put("vendors", getPlatformMembers(UserType.VENDOR, null, null, 1, 1).totalElements());
        summary.put("customers", getPlatformMembers(UserType.CUSTOMER, null, null, 1, 1).totalElements());
        summary.put("pendingReviews", getPlatformMembers(UserType.VENDOR, null, "UNDER_REVIEW", 1, 1).totalElements());
        return summary;
    }

    // Join memberships to live profiles; project safe fields before returning paginated rows.
    public PaginatedData getPlatformMembers(UserType role, String search, String eligibility, int page, int size) {
        requireRoadPlatform();
        if (role != UserType.VENDOR && role != UserType.CUSTOMER)
            throw new InvalidInputException("Choose VENDOR or CUSTOMER");
        if (page < 1 || page > 10000 || size < 1 || size > 100) throw new InvalidInputException("Invalid page or size");
        if (search != null && search.length() > 100) throw new InvalidInputException("Search is too long");
        List<Document> stages = new ArrayList<>();
        stages.add(new Document("$match", new Document("servicePlatformType", "ROAD_RESQ").append("userType", role.name())));
        stages.add(new Document("$set", new Document("userKey", new Document("$convert", new Document("input", "$userId").append("to", "objectId").append("onError", "$userId").append("onNull", "$userId")))));
        stages.add(new Document("$lookup", new Document("from", "user").append("localField", "userKey").append("foreignField", "_id").append("as", "user")));
        stages.add(new Document("$unwind", "$user"));
        stages.add(new Document("$lookup", new Document("from", role == UserType.VENDOR ? "vendors" : "customers").append("localField", "userId").append("foreignField", "userId").append("as", "profile")));
        stages.add(new Document("$unwind", "$profile"));
        Document match = new Document("user.isDeleted", new Document("$ne", true)).append("profile.deleted", new Document("$ne", true));
        if (search != null && !search.isBlank()) {
            Pattern term = Pattern.compile(Pattern.quote(search.trim()), Pattern.CASE_INSENSITIVE);
            match.append("$or", List.of(new Document("user.firstName", term), new Document("user.lastName", term), new Document("user.emailAddress", term)));
        }
        if (eligibility != null && !eligibility.isBlank()) {
            if (role != UserType.VENDOR) throw new InvalidInputException("Eligibility applies to vendors only");
            try {
                com.roadresq.auth.utils.Eligibility.valueOf(eligibility);
            } catch (IllegalArgumentException ex) {
                throw new InvalidInputException("Invalid eligibility");
            }
            match.append("profile.eligibility", eligibility);
        }
        stages.add(new Document("$match", match));
        Document projection = new Document("_id", 0).append("membershipId", new Document("$toString", "$_id")).append("userId", 1).append("role", "$userType")
                .append("membershipActive", "$isActive").append("profileId", new Document("$toString", "$profile._id"))
                .append("firstName", "$user.firstName").append("lastName", "$user.lastName").append("emailAddress", "$user.emailAddress")
                .append("phoneNumber", "$user.phoneNumber").append("profilePictureUrl", "$user.profilePictureUrl")
                .append("userActive", "$user.isActive").append("verified", "$user.isVerified").append("profileActive", "$profile.active")
                .append("eligibility", "$profile.eligibility").append("reviewReason", "$profile.reviewReason")
                .append("submittedAt", "$profile.submittedAt").append("reviewedAt", "$profile.reviewedAt").append("createdAt", "$profile.createdAt");
        stages.add(new Document("$project", projection));
        stages.add(new Document("$facet", new Document("rows", List.of(new Document("$sort", new Document("createdAt", -1).append("membershipId", 1)), new Document("$skip", (page - 1) * size), new Document("$limit", size)))
                .append("count", List.of(new Document("$count", "total")))));
        Document result = mongoTemplate.getCollection("platform_memberships").aggregate(stages).first();
        List<Document> rows = result == null ? List.of() : result.getList("rows", Document.class);
        List<Document> counts = result == null ? List.of() : result.getList("count", Document.class);
        for (Document row : rows)
            for (String field : List.of("createdAt", "submittedAt", "reviewedAt")) {
                if (row.get(field) instanceof java.util.Date date) row.put(field, date.toInstant().toString());
            }
        long total = counts.isEmpty() ? 0 : ((Number) counts.getFirst().get("total")).longValue();
        return new PaginatedData((int) Math.ceil((double) total / size), rows.size(), total, rows);
    }

     @Transactional
    public void updatePlatformAccess(String membershipId, AdminAccessRequest request) {
        requireRoadPlatform();
        PlatformMemberShip member = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Platform membership not found"));
        if (member.getServicePlatformType() != ServicePlatformType.ROAD_RESQ || (member.getUserType() != UserType.VENDOR && member.getUserType() != UserType.CUSTOMER)) {
            throw new InvalidOperationException("Only Road ResQ customer/vendor access can be managed here");
        }
        boolean before = member.isActive();
        userService.updatePlatformMemberShip(member.getId(), request.active());
        adminActivityService.record("PLATFORM_ACCESS_UPDATED", member.getId(), request.reason().trim(), String.valueOf(before), String.valueOf(request.active()));
    }

    private void requireRoadPlatform() {
        getCurrentAdminProfile();
        if (userService.getRequestedServicePlatformType() != ServicePlatformType.ROAD_RESQ)
            throw new InvalidOperationException("Road ResQ platform is required");
    }


    public PaginatedData getAdminPage(int page, int size) {
        requireSuperAdmin();
        PaginatedData result = paginationService.paginate(new Query(Criteria.where("deleted").is(false)), Admin.class,
                page, size, "createdAt", "DESC", Set.of("createdAt"));
        List<?> rows = (List<?>) result.data();
        List<AdminResponse> profiles = rows.stream().map(row -> (Admin) row)
                .map(row -> mapToResponse(row, userService.getAdminUserByIdFromAdminModule(row.getUserId()))).toList();
        return new PaginatedData(result.totalPage(), result.numberOfElements(), result.totalElements(), profiles);
    }

}