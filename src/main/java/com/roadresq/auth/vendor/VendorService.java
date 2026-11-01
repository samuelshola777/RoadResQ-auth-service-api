package com.roadresq.auth.vendor;

import com.roadresq.auth.vendor.request.*;
import com.roadresq.auth.vendor.response.*;
import com.roadresq.auth.utils.Eligibility;
import com.roadresq.auth.userauthmgt.user.User;
import com.roadresq.auth.userauthmgt.user.UserService;
import com.roadresq.auth.userauthmgt.user.request.UserCreateRequest;
import com.roadresq.auth.userauthmgt.user.request.UserAuthRequest;
import com.roadresq.auth.userauthmgt.user.request.UserResetPasswordRequest;
import com.roadresq.auth.userauthmgt.user.response.UserCreateResponse;
import com.roadresq.auth.userauthmgt.user.response.UserResponse;
import com.roadresq.auth.userauthmgt.user.response.UserVerifyOtpRequest;
import com.roadresq.auth.utils.ServicePlatformType;
import com.roadresq.auth.userauthmgt.user.UserType;
import com.roadresq.auth.userauthmgt.user.request.PasswordChangeRequest;
import com.roadresq.auth.userauthmgt.userotp.UserOtpService;
import com.roadresq.auth.userauthmgt.userotp.UserOtpType;
import com.roadresq.auth.utils.PaginatedData;
import com.roadresq.auth.utils.UtilsService;
import com.roadresq.auth.utils.appsecurity.JwtService;
import com.roadresq.auth.utils.emailsenderservice.EmailSenderService;
import com.roadresq.auth.utils.exception.*;
import com.roadresq.auth.utils.fileupload.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VendorService {
    private final VendorRepository vendorRepository;
    private final UserService userService;
    private final UserOtpService userOtpService;
    private final JwtService jwtService;
    private final EmailSenderService emailSenderService;
    private final FileUploadService fileUploadService;
    private final UtilsService utilsService;

    @Transactional
    public void register(VendorCreateRequest request) {
        log.info("Registering new vendor with email: {}", request != null ? request.emailAddress() : "null");
        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        validatePassword(request.password());

        UserCreateResponse createdUser = userService.createUser(
                UserCreateRequest.builder()
                        .firstName(requireText(request.firstName(), "First name is required"))
                        .lastName(requireText(request.lastName(), "Last name is required"))
                        .emailAddress(normalizeEmail(request.emailAddress()))
                        .password(request.password())
                        .userType(UserType.VENDOR)
                        .build()
        );

        User savedUser = userService.findById(createdUser.userId());

        Vendor vendor = new Vendor();
        vendor.setUserId(savedUser.getId());
        vendorRepository.save(vendor);

        emailSenderService.sendAccountActivationEmail(
                savedUser.getEmailAddress(),
                savedUser.getId(),
                fullName(savedUser),
                requireText(createdUser.otp(), "Activation OTP could not be generated")
        );

        log.info("Vendor registration completed for email: {}. Awaiting verification.", savedUser.getEmailAddress());
    }

    @Transactional
    public VendorResponse verifyAccount(VendorVerifyOtpRequest request) {
        log.info("Verifying vendor account for email: {}", request != null ? request.emailAddress() : "null");
        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        User user = findVendorUserByEmail(request.emailAddress());
        Vendor vendor = findVendorByUserId(user.getId());

        if (user.isVerified()) {
            log.info("Verification failed: Account already verified for email: {}", user.getEmailAddress());
            throw new InvalidOperationException("Vendor account is already verified");
        }

        User savedUser = userService.verifyOtp(
                new UserVerifyOtpRequest(
                        request.otp(),
                        user.getEmailAddress(),
                        UserOtpType.ACCOUNT_ACTIVATION
                ),
                ServicePlatformType.ROAD_RESQ
        );

        vendor.setActive(true);
        touch(vendor);

        userService.sendAccountSetupCompletionEmail(savedUser.getId());

        log.info("Vendor account verified successfully for email: {}", savedUser.getEmailAddress());

        return VendorResponse.from(vendor, savedUser);
    }

    @Transactional
    public void resendVerificationOtp(VendorResendOtpRequest request) {
        log.info("Resending OTP for email: {}, type: {}", request.emailAddress(), request.userOtpType());
        User user = findVendorUserByEmail(request.emailAddress());

        if (!user.getUserType().equals(UserType.VENDOR)) {
            log.info("Resend OTP failed: Vendor account not found for email: {}", request.emailAddress());
            throw new InvalidOperationException("Vendor account Not Found");
        }

        Vendor vendor = findVendorByUserId(user.getId());

        if (request.userOtpType() == UserOtpType.ACCOUNT_ACTIVATION) {
            if (user.isVerified()) {
                log.info("Resend OTP failed: Account already verified for email: {}", user.getEmailAddress());
                throw new InvalidOperationException("Vendor account is already verified");
            }
            sendActivationOtp(user);
            log.info("Resent ACCOUNT_ACTIVATION OTP to email: {}", user.getEmailAddress());
        } else if (request.userOtpType() == UserOtpType.PASSWORD_RESET) {
            requireActive(user, vendor);
            sendResetOtp(user);
            log.info("Resent PASSWORD_RESET OTP to email: {}", user.getEmailAddress());
        } else {
            log.info("Resend OTP failed: Invalid OTP type requested: {}", request.userOtpType());
            throw new InvalidInputException("Use ACCOUNT_ACTIVATION or PASSWORD_RESET");
        }
    }

    @Transactional
    public VendorAuthResponse login(VendorLoginRequest request) {
        log.info("Vendor login attempt for email: {}", request != null ? request.emailAddress() : "null");
        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        UserResponse authenticatedUser;

        try {
            authenticatedUser = userService.authenticateUser(
                    new UserAuthRequest(
                            normalizeEmail(request.emailAddress()),
                            request.password(),
                            null
                    ),
                    UserType.VENDOR
            );
        } catch (InvalidInputException exception) {
            findVendorUserByEmail(request.emailAddress());
            log.info("Login failed: Invalid credentials for email: {}", request.emailAddress());
            throw new UnauthorizedException("Invalid email address or password");
        } catch (InvalidOperationException exception) {
            User account = findVendorUserByEmail(request.emailAddress());
            if (!account.isVerified()) {
                log.info("Login failed: Account not verified for email: {}", request.emailAddress());
                throw new InvalidOperationException("Vendor account has not been verified");
            }
            if (!account.isActive()) {
                log.info("Login failed: Account inactive for email: {}", request.emailAddress());
                throw new UnauthorizedException("Vendor account is inactive");
            }
            log.info("Login failed: Authentication operation failed for email: {}", request.emailAddress());
            throw exception;
        }

        User user = findVendorUserById(authenticatedUser.userId());
        Vendor vendor = findVendorByUserId(user.getId());
        requireActive(user, vendor);
        ServicePlatformType platform = userService.getRequestedServicePlatformType();
        userService.requirePlatformAccess(user, UserType.VENDOR, platform);

        LocalDateTime now = LocalDateTime.now();
        user.setLastLoginAt(now);
        user.setUpdatedAt(now);
        vendor.setLastLoginAt(now);
        User savedUser = userService.updateUser(user);
        touch(vendor);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", savedUser.getId());
        claims.put("vendorId", vendor.getId());
        claims.put("email", savedUser.getEmailAddress());
        claims.put("userType", UserType.VENDOR.name());
        claims.put("userRole", "VENDOR");
        claims.put("auth", List.of("ROLE_VENDOR"));
        claims.put("servicePlatformType", platform.name());

        String accessToken = jwtService.generateToken(savedUser.getEmailAddress(), claims);
        String refreshToken = jwtService.generateRefreshToken(savedUser.getEmailAddress(), claims);

        if (accessToken == null || accessToken.isBlank() || refreshToken == null || refreshToken.isBlank()) {
            log.info("Login failed: Could not generate tokens for email: {}", savedUser.getEmailAddress());
            throw new ErrorProcessingRequestException("Unable to create the sign-in session");
        }

        log.info("Vendor login successful for email: {}", savedUser.getEmailAddress());

        return new VendorAuthResponse(accessToken, refreshToken, VendorResponse.from(vendor, savedUser));
    }

    public VendorResponse getCurrentProfile() {
        log.info("Fetching current vendor profile");
        User user = currentUser();
        Vendor vendor = currentVendor(user);
        userService.requirePlatformAccess(user, UserType.VENDOR, userService.getRequestedServicePlatformType());
        return VendorResponse.from(vendor, user);
    }

    @Transactional
    public VendorResponse updateCurrentProfile(VendorUpdateRequest request) {
        log.info("Updating vendor profile");
        User user = currentUser();
        Vendor vendor = currentVendor(user);
        String previousPicture = user.getProfilePictureUrl();

        if (request.firstName() != null) user.setFirstName(requireText(request.firstName(), "First name cannot be blank"));
        if (request.lastName() != null) user.setLastName(requireText(request.lastName(), "Last name cannot be blank"));
        if (request.phoneNumber() != null) user.setPhoneNumber(requireText(request.phoneNumber(), "Phone number cannot be blank"));
        if (request.profilePictureUrl() != null) user.setProfilePictureUrl(clean(request.profilePictureUrl()));

        user.setUpdatedAt(LocalDateTime.now());
        User savedUser = userService.updateUser(user);
        touch(vendor);
        fileUploadService.deleteOldFileIfChanged(previousPicture, savedUser.getProfilePictureUrl());

        log.info("Vendor profile updated successfully for user ID: {}", savedUser.getId());
        return VendorResponse.from(vendor, savedUser);
    }

    @Transactional
    public void changePassword(PasswordChangeRequest request) {
        log.info("Changing vendor password");
        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        User user = currentUser();
        currentVendor(user);
        validatePassword(request.newPassword());

        userService.changeUserPassword(user.getId(), UserType.VENDOR, request);

        log.info("Vendor password changed successfully for user ID: {}", user.getId());
    }

    @Transactional
    public VendorForgotPasswordResponse forgotPassword(VendorForgotPasswordRequest request) {
        log.info("Processing forgot password for email: {}", request.emailAddress());
        User user = findVendorUserByEmail(request.emailAddress());
        requireActive(user, findVendorByUserId(user.getId()));
        Map<String, String> otp = sendResetOtp(user);

        log.info("Forgot password OTP sent for email: {}", user.getEmailAddress());
        return new VendorForgotPasswordResponse(user.getId(), user.getEmailAddress(), otp.get("expiresAt"));
    }

    @Transactional
    public void resetPassword(VendorResetPasswordRequest request) {
        log.info("Processing password reset for email: {}", request != null ? request.emailAddress() : "null");
        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        User user = findVendorUserByEmail(request.emailAddress());
        requireActive(user, findVendorByUserId(user.getId()));
        validatePassword(request.newPassword());

        userService.resetPassword(
                new UserResetPasswordRequest(
                        request.otp(),
                        request.newPassword(),
                        user.getEmailAddress()
                )
        );

        log.info("Vendor password reset successfully for email: {}", user.getEmailAddress());
    }

    @Transactional
    public VendorResponse submitOnboarding() {
        log.info("Submitting onboarding for review");
        User user = currentUser();
        Vendor vendor = currentVendor(user);

        if (vendor.getEligibility() == Eligibility.UNDER_REVIEW) {
            log.info("Onboarding already under review for vendor ID: {}", vendor.getId());
            return VendorResponse.from(vendor, user);
        }
        if (vendor.getEligibility() != null && !Set.of(Eligibility.PENDING, Eligibility.REJECTED).contains(vendor.getEligibility())) {
            log.info("Invalid onboarding state for vendor ID: {}. Status: {}", vendor.getId(), vendor.getEligibility());
            throw new InvalidOperationException("This vendor account cannot be submitted for review in its current status");
        }

        requireProfileComplete(vendor, user);
        vendor.setEligibility(Eligibility.UNDER_REVIEW);
        vendor.setSubmittedAt(LocalDateTime.now());
        vendor.setReviewReason(null);
        vendor.setReviewedAt(null);
        touch(vendor);

        log.info("Onboarding submitted for review for vendor ID: {}", vendor.getId());
        return VendorResponse.from(vendor, user);
    }

    public PaginatedData getVendors(int page, int size, Eligibility eligibility) {
        log.info("Fetching vendors. Page: {}, Size: {}, Eligibility: {}", page, size, eligibility);
        if (page < 1 || size < 1 || size > 100) {
            log.info("Invalid pagination parameters");
            throw new InvalidInputException("Page must be at least 1 and size must be between 1 and 100");
        }

        var pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        var result = eligibility == null
                ? vendorRepository.findAllByDeletedFalse(pageable)
                : vendorRepository.findAllByDeletedFalseAndEligibility(eligibility.name(), pageable);

        Map<String, User> users = userService.getUsersByIds(new HashSet<>(result.getContent().stream().map(Vendor::getUserId).toList()))
                .stream().filter(user -> user.getUserType() == UserType.VENDOR && !user.isDeleted())
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<VendorResponse> responses = result.getContent().stream().map(vendor -> {
            User user = users.get(vendor.getUserId());
            if (user == null) {
                log.info("Data inconsistency: Vendor account not found for ID: {}", vendor.getId());
                throw new ResourceNotFoundException("Vendor account not found");
            }
            return VendorResponse.from(vendor, user);
        }).toList();

        return utilsService.createPaginatedData(responses, page, size, result.getTotalElements());
    }

    public VendorResponse getVendorById(String vendorId) {
        log.info("Fetching vendor by ID: {}", vendorId);
        Vendor vendor = findVendorById(vendorId);
        return VendorResponse.from(vendor, findVendorUserById(vendor.getUserId()));
    }

    @Transactional
    public VendorResponse updateEligibility(String vendorId, VendorEligibilityRequest request) {
        log.info("Updating eligibility for vendor ID: {} to {}", vendorId, request != null ? request.eligibility() : "null");
        Vendor vendor = findVendorById(vendorId);
        User user = findVendorUserById(vendor.getUserId());
        Eligibility next = request.eligibility();

        if (next == null) {
            log.info("Eligibility update failed: Eligibility is null");
            throw new InvalidInputException("Eligibility is required");
        }
        if (next == Eligibility.APPROVED) {
            requireActive(user, vendor);
            requireProfileComplete(vendor, user);
        } else if (next == Eligibility.REJECTED) {
            requireText(request.reason(), "A rejection reason is required");
        } else if (next == Eligibility.SUSPENDED) {
            requireText(request.reason(), "A suspension reason is required");
        } else {
            log.info("Eligibility update failed: Invalid target status: {}", next);
            throw new InvalidInputException("Choose APPROVED, REJECTED or SUSPENDED");
        }

        vendor.setEligibility(next);
        vendor.setReviewReason(clean(request.reason()));
        vendor.setReviewedAt(LocalDateTime.now());
        touch(vendor);

        log.info("Vendor eligibility updated for ID: {} to: {}", vendor.getId(), next);
        return VendorResponse.from(vendor, user);
    }

    private void requireProfileComplete(Vendor vendor, User user) {
        requireText(user.getFirstName(), "First name is required");
        requireText(user.getLastName(), "Last name is required");
        requireText(user.getPhoneNumber(), "Phone number is required");
    }

    private void touch(Vendor vendor) {
        vendor.setUpdatedAt(LocalDateTime.now());
        vendorRepository.save(vendor);
    }

    private User currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.info("Security context validation failed: Authentication is required");
            throw new UnauthorizedException("Authentication is required");
        }
        return findVendorUserByEmail(authentication.getName());
    }

    private Vendor currentVendor(User user) {
        Vendor vendor = findVendorByUserId(user.getId());
        requireActive(user, vendor);
        return vendor;
    }

    private void requireActive(User user, Vendor vendor) {
        if (!user.isVerified()) {
            log.info("Validation failed: Vendor not verified for user ID: {}", user.getId());
            throw new InvalidOperationException("Vendor account has not been verified");
        }
        if (!user.isActive() || !vendor.isActive()) {
            log.info("Validation failed: Vendor inactive for user ID: {}", user.getId());
            throw new UnauthorizedException("Vendor account is inactive");
        }
    }

    private Vendor findVendorById(String id) {
        return vendorRepository.findByIdAndDeletedFalse(requireText(id, "Vendor ID is required"))
                .orElseThrow(() -> {
                    log.info("Vendor not found for ID: {}", id);
                    return new ResourceNotFoundException("Vendor not found");
                });
    }

    private Vendor findVendorByUserId(String userId) {
        return vendorRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> {
                    log.info("Vendor profile not found for user ID: {}", userId);
                    return new ResourceNotFoundException("Vendor profile not found");
                });
    }

    private User findVendorUserByEmail(String email) {
        return userService.findByEmailAndUserType(normalizeEmail(email), UserType.VENDOR)
                .orElseThrow(() -> {
                    log.info("Vendor account not found for email: {}", email);
                    return new ResourceNotFoundException("Vendor account not found");
                });
    }

    private User findVendorUserById(String userId) {
        User user = userService.findById(userId);
        if (user.getUserType() != UserType.VENDOR || user.isDeleted()) {
            log.info("User is not a valid vendor for ID: {}", userId);
            throw new ResourceNotFoundException("Vendor account not found");
        }
        return user;
    }

    private void sendActivationOtp(User user) {
        Map<String, String> otp = userOtpService.generateOtpForUser(user.getId(), UserOtpType.ACCOUNT_ACTIVATION);
        emailSenderService.sendAccountActivationEmail(user.getEmailAddress(), user.getId(), fullName(user), otp.get("otp"));
    }

    private Map<String, String> sendResetOtp(User user) {
        Map<String, String> otp = userOtpService.generateOtpForUser(user.getId(), UserOtpType.PASSWORD_RESET);
        emailSenderService.sendPasswordResetEmail(user.getEmailAddress(), user.getId(), otp.get("otp"), fullName(user));
        return otp;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 100) {
            log.info("Password validation failed: Length out of bounds");
            throw new InvalidPasswordException("Password must contain 8–100 characters");
        }
    }

    private Set<String> cleanAreas(Set<String> areas, boolean required) {
        if (areas == null || (required && areas.isEmpty()) || areas.size() > 50) {
            log.info("Coverage area validation failed");
            throw new InvalidInputException("Provide between 1 and 50 coverage areas");
        }
        Set<String> result = new LinkedHashSet<>();
        for (String area : areas) {
            String value = requireText(area, "Coverage areas cannot be blank");
            if (value.length() > 100) {
                log.info("Coverage area exceeds 100 characters");
                throw new InvalidInputException("Coverage areas cannot exceed 100 characters");
            }
            result.add(value);
        }
        return result;
    }

    private String normalizeEmail(String email) {
        return requireText(email, "Email address is required").toLowerCase(Locale.ROOT);
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            log.info("Required text validation failed: {}", message);
            throw new InvalidInputException(message);
        }
        return value.trim();
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String fullName(User user) {
        return ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
    }
}