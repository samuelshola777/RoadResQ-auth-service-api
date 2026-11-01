package com.roadresq.auth.vendor;

import com.roadresq.auth.vendor.request.*;
import com.roadresq.auth.vendor.response.*;
import com.roadresq.auth.userauthmgt.user.request.PasswordChangeRequest;
import com.roadresq.auth.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vendor")
@RequiredArgsConstructor
public class VendorController {
    private final VendorService vendorService;

    @PostMapping("/public/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody VendorCreateRequest request) {
        vendorService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true,
                "Vendor account created. Check your email for the verification OTP", null));
    }

    @PostMapping("/public/verify-otp")
    public ResponseEntity<ApiResponse<VendorResponse>> verifyAccount(@Valid @RequestBody VendorVerifyOtpRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Vendor account verified successfully", vendorService.verifyAccount(request)));
    }

    @PostMapping("/public/resend-otp")
    public ResponseEntity<ApiResponse<VendorOtpResponse>> resendOtp(@Valid @RequestBody VendorResendOtpRequest request) {
        vendorService.resendVerificationOtp(request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Verification OTP sent successfully", null));
    }

    @PostMapping("/public/login")
    public ResponseEntity<ApiResponse<VendorAuthResponse>> login(@Valid @RequestBody VendorLoginRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Vendor login successful", vendorService.login(request)));
    }

    @PostMapping("/public/forgot-password")
    public ResponseEntity<ApiResponse<VendorForgotPasswordResponse>> forgotPassword(@Valid @RequestBody VendorForgotPasswordRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Password reset OTP sent successfully", vendorService.forgotPassword(request)));
    }

    @PostMapping("/public/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody VendorResetPasswordRequest request) {
        vendorService.resetPassword(request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Password reset successfully", null));
    }

    @GetMapping("/private/platform-access")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<VendorResponse>> checkPlatformAccess(
            @RequestAttribute("validatedProfile") VendorResponse profile) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Platform access verified", profile));
    }

    @GetMapping("/private/profile")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<VendorResponse>> getProfile() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Vendor profile retrieved successfully", vendorService.getCurrentProfile()));
    }

    @PatchMapping("/private/profile")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<VendorResponse>> updateProfile(@Valid @RequestBody VendorUpdateRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Vendor profile updated successfully", vendorService.updateCurrentProfile(request)));
    }

    @PatchMapping("/private/change-password")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        vendorService.changePassword(request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Password changed successfully", null));
    }



    @PostMapping("/private/onboarding/submit")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<VendorResponse>> submitOnboarding() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Vendor profile submitted for review", vendorService.submitOnboarding()));
    }

    @GetMapping("/private/admin/{vendorId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<VendorResponse>> getVendor(@PathVariable String vendorId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Vendor retrieved successfully", vendorService.getVendorById(vendorId)));
    }

    @PatchMapping("/private/admin/{vendorId}/eligibility")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<VendorResponse>> updateEligibility(@PathVariable String vendorId, @Valid @RequestBody VendorEligibilityRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Vendor eligibility updated successfully", vendorService.updateEligibility(vendorId, request)));
    }
}