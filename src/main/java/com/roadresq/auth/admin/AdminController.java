package com.roadresq.auth.admin;


import com.roadresq.auth.admin.request.*;
import com.roadresq.auth.utils.ApiResponse;
import com.roadresq.auth.admin.response.AdminAuthResponse;
import com.roadresq.auth.admin.response.AdminResponse;
import com.roadresq.auth.utils.exception.InvalidOperationException;
import com.roadresq.auth.utils.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final com.roadresq.auth.adminActivity.AdminActivityService adminActivityService;

    @PostMapping("/public/login")
    public ResponseEntity<AdminAuthResponse> login(
            @Valid @RequestBody AdminLoginRequest request
    ) {
        return ResponseEntity.ok(adminService.login(request));
    }

    @PostMapping("/public/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody AdminForgotPasswordRequest request
    ) {
        try {
            // The service must deliver the OTP through email.
            // Do not expose the returned OTP in this public response.
            adminService.initiateAdminForgotPassword(request);
        } catch (ResourceNotFoundException | InvalidOperationException ignored) {
            // Keep missing or ineligible accounts indistinguishable.
        }

        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "If the account is eligible, password reset instructions will be sent."
                )
        );
    }

    @PostMapping("/public/reset-password")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody AdminResetPasswordRequest request
    ) {
        adminService.resetAdminPassword(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/private/platform-access")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdminResponse>> checkPlatformAccess(
            @RequestAttribute("validatedProfile") AdminResponse profile) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Admin access verified", profile));
    }

    @GetMapping("/private/me")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<AdminResponse> getCurrentAdminProfile() {
        return ResponseEntity.ok(adminService.getCurrentAdminProfile());
    }

    @PostMapping("/private")

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<AdminResponse> createAdmin(
            @Valid @RequestBody AdminCreateRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(adminService.createAdmin(request));
    }

    @GetMapping("/private")

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<AdminResponse>> getAdmins() {
        return ResponseEntity.ok(adminService.getAdmins());
    }

    @GetMapping("/private/{adminId}")

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<AdminResponse> getAdminById(
            @PathVariable("adminId") String adminId
    ) {
        return ResponseEntity.ok(adminService.getAdminById(adminId));
    }

    @PutMapping("/private/{adminId}")

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<AdminResponse> updateAdmin(
            @PathVariable("adminId") String adminId,
            @Valid @RequestBody AdminUpdateRequest request
    ) {
        return ResponseEntity.ok(
                adminService.updateAdmin(adminId, request)
        );
    }

    @PatchMapping("/private/{adminId}/activate")

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<AdminResponse> activateAdmin(
            @PathVariable("adminId") String adminId
    ) {
        return ResponseEntity.ok(adminService.activateAdmin(adminId));
    }

    @PatchMapping("/private/{adminId}/deactivate")

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<AdminResponse> deactivateAdmin(
            @PathVariable("adminId") String adminId
    ) {
        return ResponseEntity.ok(adminService.deactivateAdmin(adminId));
    }

    @DeleteMapping("/private/{adminId}")

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteAdmin(
            @PathVariable("adminId") String adminId
    ) {
        adminService.deleteAdmin(adminId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/private/count")

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Long> countAllAdmins() {
        return ResponseEntity.ok(adminService.countAllAdmins());
    }

    @GetMapping("/private/count/active")

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Long> countActiveAdmins() {
        return ResponseEntity.ok(adminService.countActiveAdmins());
    }

    @GetMapping("/private/count/inactive")

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Long> countInactiveAdmins() {
        return ResponseEntity.ok(adminService.countInactiveAdmins());
    }

    @GetMapping("/private/portal/analytics")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> analytics(@RequestParam(defaultValue = "30") int days) {
        return new ApiResponse<>(true, "Admin analytics retrieved", adminService.getPortalAnalytics(days));
    }

    @GetMapping("/private/portal/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<Map<String, Long>> summary() {
        return new ApiResponse<>(true, "Admin summary retrieved", adminService.getPortalSummary());
    }

    @GetMapping("/private/portal/members")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<com.roadresq.auth.utils.PaginatedData> members(
            @RequestParam com.roadresq.auth.userauthmgt.user.UserType role,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String eligibility,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return new ApiResponse<>(true, "Platform members retrieved", adminService.getPlatformMembers(role, search, eligibility, page, size));
    }

    @PatchMapping("/private/portal/memberships/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<Void> updateAccess(@PathVariable String id, @Valid @RequestBody AdminAccessRequest request) {
        adminService.updatePlatformAccess(id, request);
        return new ApiResponse<>(true, "Road ResQ access updated", null);
    }

    @GetMapping("/private/portal/activity")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<com.roadresq.auth.utils.PaginatedData> activity(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return new ApiResponse<>(true, "Admin activity retrieved", adminActivityService.getActivity(page, size));
    }

    @PatchMapping("/private/profile")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<AdminResponse> updateProfile(@Valid @RequestBody AdminUpdateRequest request) {
        return new ApiResponse<>(true, "Profile updated", adminService.updateMyProfile(request));
    }

    @PatchMapping("/private/change-password")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<Void> changePassword(@Valid @RequestBody com.roadresq.auth.userauthmgt.user.request.PasswordChangeRequest request) {
        adminService.changeMyPassword(request);
        return new ApiResponse<>(true, "Password changed. Sign in again", null);
    }


    @GetMapping("/private/portal/admins")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<com.roadresq.auth.utils.PaginatedData> adminPage(@RequestParam(defaultValue = "1") int page,
                                                                        @RequestParam(defaultValue = "20") int size) {
        return new ApiResponse<>(true, "Administrators retrieved", adminService.getAdminPage(page, size));
    }

}