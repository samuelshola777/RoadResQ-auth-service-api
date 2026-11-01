package com.roadresq.auth.customer;

import com.roadresq.auth.customer.request.*;
import com.roadresq.auth.customer.response.CustomerAuthResponse;
import com.roadresq.auth.customer.response.CustomerForgotPasswordResponse;
import com.roadresq.auth.customer.response.CustomerResponse;
import com.roadresq.auth.userauthmgt.user.User;
import com.roadresq.auth.userauthmgt.user.UserService;
import com.roadresq.auth.userauthmgt.user.UserType;
import com.roadresq.auth.userauthmgt.user.request.PasswordChangeRequest;
import com.roadresq.auth.userauthmgt.user.request.UserCreateRequest;
import com.roadresq.auth.userauthmgt.user.request.UserAuthRequest;
import com.roadresq.auth.userauthmgt.user.request.UserResetPasswordRequest;
import com.roadresq.auth.userauthmgt.user.response.UserResponse;
import com.roadresq.auth.userauthmgt.user.response.UserVerifyOtpRequest;
import com.roadresq.auth.userauthmgt.user.response.UserCreateResponse;
import com.roadresq.auth.userauthmgt.userotp.UserOtpService;
import com.roadresq.auth.userauthmgt.userotp.UserOtpType;
import com.roadresq.auth.utils.PaginatedData;
import com.roadresq.auth.utils.ServicePlatformType;
import com.roadresq.auth.utils.UtilsService;
import com.roadresq.auth.utils.appsecurity.JwtService;
import com.roadresq.auth.utils.emailsenderservice.EmailSenderService;
import com.roadresq.auth.utils.exception.*;
import com.roadresq.auth.utils.fileupload.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final UserService userService;
    private final UserOtpService userOtpService;
    private final JwtService jwtService;
    private final EmailSenderService emailSenderService;
    private final FileUploadService fileUploadService;
    private final UtilsService utilsService;
    private final MongoTemplate mongoTemplate;

    @Transactional
    public void register(CustomerCreateRequest request) {
        log.info("Attempting to register new customer with email: {}", request != null ? request.emailAddress() : "null");
        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        validatePassword(request.password());

        UserCreateResponse createdUser = userService.createUser(
                UserCreateRequest.builder()
                        .firstName(request.firstName())
                        .lastName(request.lastName())
                        .emailAddress(normalizeEmail(request.emailAddress()))
                        .password(request.password())
                        .userType(UserType.CUSTOMER)
                        .build()
        );

        User savedUser = userService.findById(createdUser.userId());

        Customer customer = Customer.builder()
                .userId(savedUser.getId())
                .customerNumber(generateCustomerNumber())
                .active(false)
                .deleted(false)
                .lastLoginAt(null)
                .deletedAt(null)
                .build();

        customerRepository.save(customer);

        emailSenderService.sendAccountActivationEmail(
                savedUser.getEmailAddress(),
                savedUser.getId(),
                buildFullName(savedUser),
                requireText(createdUser.otp(), "Activation OTP could not be generated")
        );

        log.info("Customer registration completed; email verification is required for email: {}", savedUser.getEmailAddress());
    }

    @Transactional
    public CustomerResponse verifyAccount(CustomerVerifyOtpRequest request) {
        log.info("Attempting to verify customer account for email: {}", request != null ? request.emailAddress() : "null");
        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        User user = findCustomerUserByEmail(request.emailAddress());
        Customer customer = findCustomerByUserId(user.getId());

        if (user.isVerified()) {
            log.info("Customer verification failed: Account already verified for email: {}", user.getEmailAddress());
            throw new InvalidOperationException("Customer account is already verified");
        }

        User savedUser = userService.verifyOtp(
                new UserVerifyOtpRequest(
                        request.otp(),
                        user.getEmailAddress(),
                        UserOtpType.ACCOUNT_ACTIVATION
                ),
                ServicePlatformType.ROAD_RESQ
        );

        customer.setActive(true);
        customer.setUpdatedAt(LocalDateTime.now());

        Customer savedCustomer = customerRepository.save(customer);

        userService.sendAccountSetupCompletionEmail(savedUser.getId());

        log.info("Customer account verified successfully for email: {}", savedUser.getEmailAddress());

        return CustomerResponse.from(savedCustomer, savedUser);
    }

    public void resendVerificationOtp(CustomerResendOtpRequest request) {
        log.info("Attempting to resend OTP for email: {}, type: {}", request.emailAddress(), request.userOtpType());
        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        User user = findCustomerUserByEmail(request.emailAddress());

        if (request.userOtpType() == UserOtpType.ACCOUNT_ACTIVATION) {
            if (user.isVerified()) {
                log.info("Resend OTP failed: Account already verified for email: {}", user.getEmailAddress());
                throw new InvalidOperationException("Customer account is already verified");
            }

            Map<String, String> otp = userOtpService.generateOtpForUser(
                    user.getId(),
                    UserOtpType.ACCOUNT_ACTIVATION
            );

            emailSenderService.sendAccountActivationEmail(
                    user.getEmailAddress(),
                    user.getId(),
                    buildFullName(user),
                    otp.get("otp")
            );
        } else if (request.userOtpType() == UserOtpType.PASSWORD_RESET) {
            Map<String, String> otp = userOtpService.generateOtpForUser(
                    user.getId(),
                    UserOtpType.PASSWORD_RESET
            );

            emailSenderService.sendPasswordResetEmail(
                    user.getEmailAddress(),
                    user.getId(),
                    otp.get("otp"),
                    buildFullName(user)
            );
        } else {
            log.info("Resend OTP failed: Invalid OTP type requested: {}", request.userOtpType());
            throw new InvalidInputException("Use ACCOUNT_ACTIVATION or PASSWORD_RESET");
        }

        log.info("Customer OTP email submitted successfully: purpose={}", request.userOtpType());
    }

    @Transactional
    public CustomerAuthResponse login(CustomerLoginRequest request) {
        log.info("Attempting customer login for email: {}", request != null ? request.emailAddress() : "null");
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
                    UserType.CUSTOMER
            );
        } catch (InvalidInputException exception) {
            findCustomerUserByEmail(request.emailAddress());
            log.info("Login failed: Invalid credentials for email: {}", request.emailAddress());
            throw new UnauthorizedException("Invalid email address or password");
        } catch (InvalidOperationException exception) {
            User account = findCustomerUserByEmail(request.emailAddress());

            if (!account.isVerified()) {
                log.info("Login failed: Account not verified for email: {}", request.emailAddress());
                throw new InvalidOperationException("Customer account has not been verified");
            }

            if (!account.isActive()) {
                log.info("Login failed: Account inactive for email: {}", request.emailAddress());
                throw new UnauthorizedException("Customer account is inactive");
            }

            log.info("Login failed: Authentication operation failed for email: {}", request.emailAddress());
            throw exception;
        }

        User user = findCustomerUserById(authenticatedUser.userId());
        Customer customer = findCustomerByUserId(user.getId());

        if (!customer.isActive()) {
            log.info("Login failed: Customer profile inactive for user ID: {}", user.getId());
            throw new UnauthorizedException("Customer account is inactive");
        }

        userService.requirePlatformAccess(user, UserType.CUSTOMER, userService.getRequestedServicePlatformType());

        LocalDateTime loginTime = LocalDateTime.now();

        user.setLastLoginAt(loginTime);
        customer.setLastLoginAt(loginTime);
        customer.setUpdatedAt(loginTime);

        User savedUser = userService.updateUser(user);
        Customer savedCustomer = customerRepository.save(customer);

        Map<String, Object> claims = buildJwtClaims(savedCustomer, savedUser);

        String accessToken = jwtService.generateToken(savedUser.getEmailAddress(), claims);
        String refreshToken = jwtService.generateRefreshToken(savedUser.getEmailAddress(), claims);

        if (accessToken == null || accessToken.isBlank() || refreshToken == null || refreshToken.isBlank()) {
            log.info("Login failed: Could not generate tokens for email: {}", savedUser.getEmailAddress());
            throw new ErrorProcessingRequestException("Unable to create the sign-in session");
        }

        log.info("Customer login completed successfully for email: {}", savedUser.getEmailAddress());

        return new CustomerAuthResponse(
                accessToken,
                refreshToken,
                CustomerResponse.from(savedCustomer, savedUser)
        );
    }

    public CustomerResponse getCurrentProfile() {
        log.info("Fetching current customer profile");
        User user = findCustomerUserByEmail(getCurrentPrincipalEmail());
        Customer customer = findCustomerByUserId(user.getId());

        if (!customer.isActive()) {
            log.info("Profile fetch failed: Customer account inactive for user ID: {}", user.getId());
            throw new UnauthorizedException("Customer account is inactive");
        }

        userService.requirePlatformAccess(user, UserType.CUSTOMER, userService.getRequestedServicePlatformType());
        return CustomerResponse.from(customer, user);
    }

    @Transactional
    public CustomerResponse updateCurrentProfile(CustomerUpdateRequest request) {
        log.info("Updating current customer profile");
        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        User user = findCustomerUserByEmail(getCurrentPrincipalEmail());
        Customer customer = findCustomerByUserId(user.getId());

        String previousProfilePicture = user.getProfilePictureUrl();

        if (request.firstName() != null) user.setFirstName(requireText(request.firstName(), "First name cannot be blank"));
        if (request.lastName() != null) user.setLastName(requireText(request.lastName(), "Last name cannot be blank"));
        if (request.phoneNumber() != null) user.setPhoneNumber(clean(request.phoneNumber()));
        if (request.dateOfBirth() != null) {
            user.setDateOfBirth(request.dateOfBirth());
            user.setAge(calculateAge(request.dateOfBirth()));
        }
        if (request.gender() != null) user.setGender(request.gender());
        if (request.profilePictureUrl() != null) user.setProfilePictureUrl(clean(request.profilePictureUrl()));
        if (request.marketingEmailsEnabled() != null) customer.setMarketingEmailsEnabled(request.marketingEmailsEnabled());

        user.setUpdatedAt(LocalDateTime.now());
        customer.setUpdatedAt(LocalDateTime.now());

        User savedUser = userService.updateUser(user);
        Customer savedCustomer = customerRepository.save(customer);

        fileUploadService.deleteOldFileIfChanged(previousProfilePicture, savedUser.getProfilePictureUrl());

        log.info("Customer profile updated successfully for user ID: {}", savedUser.getId());
        return CustomerResponse.from(savedCustomer, savedUser);
    }

    @Transactional
    public void changePassword(PasswordChangeRequest request) {
        log.info("Attempting to change customer password");
        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        validatePassword(request.newPassword());

        User user = findCustomerUserByEmail(getCurrentPrincipalEmail());

        userService.changeUserPassword(user.getId(), UserType.CUSTOMER, request);

        log.info("Customer password changed successfully for user ID: {}", user.getId());
    }

    public CustomerForgotPasswordResponse forgotPassword(CustomerForgotPasswordRequest request) {
        log.info("Processing forgot password request for email: {}", request.emailAddress());
        User user = findCustomerUserByEmail(request.emailAddress());
        Customer customer = findCustomerByUserId(user.getId());

        if (!user.isActive() || !customer.isActive()) {
            log.info("Forgot password failed: Account inactive for email: {}", user.getEmailAddress());
            throw new InvalidOperationException("Customer account is inactive");
        }

        if (!user.isVerified()) {
            log.info("Forgot password failed: Account not verified for email: {}", user.getEmailAddress());
            throw new InvalidOperationException("Customer account has not been verified");
        }

        Map<String, String> otp = userOtpService.generateOtpForUser(user.getId(), UserOtpType.PASSWORD_RESET);

        emailSenderService.sendPasswordResetEmail(
                user.getEmailAddress(),
                user.getId(),
                otp.get("otp"),
                buildFullName(user)
        );

        log.info("Forgot password OTP sent successfully for email: {}", user.getEmailAddress());
        return new CustomerForgotPasswordResponse(user.getId(), user.getEmailAddress(), otp.get("expiresAt"));
    }

    @Transactional
    public void resetPassword(CustomerResetPasswordRequest request) {
        log.info("Processing password reset for email: {}", request != null ? request.emailAddress() : "null");
        if (request == null) {
            throw new InvalidInputException("Request is required");
        }

        User user = findCustomerUserByEmail(request.emailAddress());
        Customer customer = findCustomerByUserId(user.getId());

        if (!user.isActive() || !customer.isActive()) {
            log.info("Reset password failed: Account inactive for email: {}", user.getEmailAddress());
            throw new InvalidOperationException("Customer account is inactive");
        }

        validatePassword(request.newPassword());

        userService.resetPassword(
                new UserResetPasswordRequest(
                        request.otp(),
                        request.newPassword(),
                        user.getEmailAddress()
                )
        );

        log.info("Customer password reset successfully for email: {}", user.getEmailAddress());
    }

    @Transactional
    public void deleteCurrentAccount() {
        log.info("Processing current account deletion");
        User user = findCustomerUserByEmail(getCurrentPrincipalEmail());
        Customer customer = findCustomerByUserId(user.getId());
        softDelete(customer, user);
        log.info("Customer account deleted successfully for user ID: {}", user.getId());
    }

    public PaginatedData getCustomers(int page, int size, String searchParam) {
        log.info("Fetching customers list. Page: {}, Size: {}, Search: {}", page, size, searchParam);
        validatePagination(page, size);

        Criteria criteria = Criteria.where("deleted").is(false);

        if (StringUtils.hasText(searchParam)) {
            Query userQuery = new Query();
            userQuery.addCriteria(
                    new Criteria().andOperator(
                            Criteria.where("userType").is(UserType.CUSTOMER),
                            Criteria.where("isDeleted").is(false),
                            new Criteria().orOperator(
                                    Criteria.where("firstName").regex(searchParam, "i"),
                                    Criteria.where("lastName").regex(searchParam, "i"),
                                    Criteria.where("emailAddress").regex(searchParam, "i"),
                                    Criteria.where("phoneNumber").regex(searchParam, "i")
                            )
                    )
            );

            List<String> matchingUserIds = userService.findUsers(userQuery)
                    .stream().map(User::getId).toList();

            criteria = criteria.andOperator(
                    new Criteria().orOperator(
                            Criteria.where("customerNumber").regex(searchParam, "i"),
                            Criteria.where("userId").in(matchingUserIds)
                    )
            );
        }

        return executeCustomerPagination(page, size, criteria);
    }

    public PaginatedData filterCustomers(int page, int size, CustomerFilterRequest filter) {
        log.info("Filtering customers. Page: {}, Size: {}", page, size);
        validatePagination(page, size);

        CustomerFilterRequest request = filter == null
                ? new CustomerFilterRequest(null, null, null, null, null, null, null)
                : filter;

        validateCustomerFilterDates(request);

        Criteria criteria = Criteria.where("deleted").is(false);

        if (request.marketingEmailsEnabled() != null) criteria = criteria.and("marketingEmailsEnabled").is(request.marketingEmailsEnabled());
        if (request.registeredFrom() != null) criteria = criteria.and("createdAt").gte(request.registeredFrom().atStartOfDay());
        if (request.registeredTo() != null) criteria = criteria.and("createdAt").lt(request.registeredTo().plusDays(1).atStartOfDay());

        boolean needsUserFilter = request.active() != null || request.verified() != null || request.gender() != null || StringUtils.hasText(request.searchParam());

        if (needsUserFilter) {
            Query userQuery = new Query();
            Criteria userCriteria = Criteria.where("userType").is(UserType.CUSTOMER).and("isDeleted").is(false);

            if (request.active() != null) userCriteria = userCriteria.and("isActive").is(request.active());
            if (request.verified() != null) userCriteria = userCriteria.and("isVerified").is(request.verified());
            if (request.gender() != null) userCriteria = userCriteria.and("gender").is(request.gender());

            if (StringUtils.hasText(request.searchParam())) {
                userCriteria = userCriteria.andOperator(
                        new Criteria().orOperator(
                                Criteria.where("firstName").regex(request.searchParam(), "i"),
                                Criteria.where("lastName").regex(request.searchParam(), "i"),
                                Criteria.where("emailAddress").regex(request.searchParam(), "i"),
                                Criteria.where("phoneNumber").regex(request.searchParam(), "i")
                        )
                );
            }

            userQuery.addCriteria(userCriteria);
            List<String> matchingUserIds = userService.findUsers(userQuery).stream().map(User::getId).toList();
            criteria = criteria.and("userId").in(matchingUserIds);
        }

        return executeCustomerPagination(page, size, criteria);
    }

    private PaginatedData executeCustomerPagination(int page, int size, Criteria criteria) {
        Query query = new Query(criteria)
                .with(PageRequest.of(page - 1, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));

        long total = mongoTemplate.count(query, Customer.class);
        List<Customer> customers = mongoTemplate.find(query, Customer.class);

        List<String> userIds = customers.stream().map(Customer::getUserId).distinct().toList();

        Map<String, User> usersById = userIds.isEmpty()
                ? Map.of()
                : userService.getUsersByIds(new HashSet<>(userIds))
                .stream()
                .filter(user -> user.getUserType() == UserType.CUSTOMER && !user.isDeleted())
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<CustomerResponse> responses = customers.stream().map(customer -> {
            User user = usersById.get(customer.getUserId());
            if (user == null) {
                log.info("Data inconsistency: Customer account not found for customer ID: {}", customer.getId());
                throw new ResourceNotFoundException("Customer account not found");
            }
            return CustomerResponse.from(customer, user);
        }).toList();

        return utilsService.createPaginatedData(responses, page, size, total);
    }

    public CustomerResponse getCustomerById(String customerId) {
        log.info("Fetching customer by ID: {}", customerId);
        Customer customer = findCustomerById(customerId);
        User user = findCustomerUserById(customer.getUserId());
        return CustomerResponse.from(customer, user);
    }

    @Transactional
    public CustomerResponse activateCustomer(String customerId) {
        log.info("Attempting to activate customer ID: {}", customerId);
        Customer customer = findCustomerById(customerId);
        User user = findCustomerUserById(customer.getUserId());

        if (!user.isVerified()) {
            log.info("Activation failed: Customer account not verified for ID: {}", customerId);
            throw new InvalidOperationException("Unverified customer accounts cannot be activated");
        }

        customer.setActive(true);
        customer.setUpdatedAt(LocalDateTime.now());
        user.setActive(true);
        user.setUpdatedAt(LocalDateTime.now());

        log.info("Customer activated successfully for ID: {}", customerId);
        return CustomerResponse.from(customerRepository.save(customer), userService.updateUser(user));
    }

    @Transactional
    public CustomerResponse deactivateCustomer(String customerId) {
        log.info("Attempting to deactivate customer ID: {}", customerId);
        Customer customer = findCustomerById(customerId);
        User user = findCustomerUserById(customer.getUserId());

        customer.setActive(false);
        customer.setUpdatedAt(LocalDateTime.now());
        user.setActive(false);
        user.setUpdatedAt(LocalDateTime.now());

        log.info("Customer deactivated successfully for ID: {}", customerId);
        return CustomerResponse.from(customerRepository.save(customer), userService.updateUser(user));
    }

    @Transactional
    public void deleteCustomer(String customerId) {
        log.info("Attempting to delete customer ID: {}", customerId);
        Customer customer = findCustomerById(customerId);
        User user = findCustomerUserById(customer.getUserId());
        softDelete(customer, user);
        log.info("Customer deleted successfully for ID: {}", customerId);
    }

    private void softDelete(Customer customer, User user) {
        LocalDateTime deletedAt = LocalDateTime.now();

        customer.setDeleted(true);
        customer.setActive(false);
        customer.setDeletedAt(deletedAt);
        customer.setUpdatedAt(deletedAt);

        user.setDeleted(true);
        user.setActive(false);
        user.setDeletedAt(deletedAt);
        user.setUpdatedAt(deletedAt);

        customerRepository.save(customer);
        userService.updateUser(user);

        fileUploadService.deleteFileAsync(user.getProfilePictureUrl());
    }

    private Customer findCustomerById(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            log.info("Validation failed: Customer ID is required");
            throw new InvalidInputException("Customer ID is required");
        }

        return customerRepository.findByIdAndDeletedFalse(customerId.trim())
                .orElseThrow(() -> {
                    log.info("Customer lookup failed: Customer not found for ID: {}", customerId);
                    return new ResourceNotFoundException("Customer not found");
                });
    }

    private Customer findCustomerByUserId(String userId) {
        return customerRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> {
                    log.info("Customer lookup failed: Profile not found for user ID: {}", userId);
                    return new ResourceNotFoundException("Customer profile not found");
                });
    }

    private User findCustomerUserByEmail(String emailAddress) {
        return userService.findByEmailAndUserType(normalizeEmail(emailAddress), UserType.CUSTOMER)
                .orElseThrow(() -> {
                    log.info("Customer lookup failed: Account not found for email: {}", emailAddress);
                    return new ResourceNotFoundException("Customer account not found");
                });
    }

    private User findCustomerUserById(String userId) {
        User user = userService.findById(userId);
        if (user.getUserType() != UserType.CUSTOMER || user.isDeleted()) {
            log.info("Customer lookup failed: User is not a valid active customer for ID: {}", userId);
            throw new ResourceNotFoundException("Customer account not found");
        }
        return user;
    }

    private Map<String, Object> buildJwtClaims(Customer customer, User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("customerId", customer.getId());
        claims.put("email", user.getEmailAddress());
        claims.put("userType", UserType.CUSTOMER.name());
        claims.put("userRole", "CUSTOMER");
        claims.put("auth", List.of("ROLE_CUSTOMER"));
        claims.put("servicePlatformType", userService.getRequestedServicePlatformType().name());
        return claims;
    }

    private String generateCustomerNumber() {
        String customerNumber;
        do {
            customerNumber = "HLT-CUS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        } while (customerRepository.existsByCustomerNumber(customerNumber));
        return customerNumber;
    }

    private String getCurrentPrincipalEmail() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {
            log.info("Security context validation failed: Authentication is required");
            throw new UnauthorizedException("Authentication is required");
        }
        return authentication.getName();
    }

    private String normalizeEmail(String emailAddress) {
        if (emailAddress == null || emailAddress.isBlank()) {
            log.info("Validation failed: Email address is required");
            throw new InvalidInputException("Email address is required");
        }
        return emailAddress.trim().toLowerCase(Locale.ROOT);
    }

    private String buildFullName(User user) {
        return (safeText(user.getFirstName()) + " " + safeText(user.getLastName())).trim();
    }

    private String calculateAge(LocalDate dateOfBirth) {
        if (dateOfBirth == null) return "";
        return Period.between(dateOfBirth, LocalDate.now()).getYears() + " years";
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            log.info("Password validation failed: Password must contain at least 8 characters");
            throw new InvalidPasswordException("Password must contain at least 8 characters");
        }
    }

    private void validatePagination(int page, int size) {
        if (page < 1) {
            log.info("Pagination validation failed: Page number must be at least 1");
            throw new InvalidInputException("Page number must be at least 1");
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            log.info("Required text validation failed: {}", message);
            throw new InvalidInputException(message);
        }
        return value.trim();
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private void validateCustomerFilterDates(CustomerFilterRequest filter) {
        if (filter.registeredFrom() != null && filter.registeredTo() != null && filter.registeredFrom().isAfter(filter.registeredTo())) {
            log.info("Date validation failed: Registration start date is after end date");
            throw new InvalidInputException("Registration start date cannot be after the end date");
        }

        if (LocalDate.MAX.equals(filter.registeredTo())) {
            log.info("Date validation failed: Registration end date is out of range");
            throw new InvalidInputException("Registration end date is out of range");
        }
    }
}