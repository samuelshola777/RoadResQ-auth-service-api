package com.roadresq.auth.admin;

import com.roadresq.auth.admin.request.*;
import com.roadresq.auth.admin.response.AdminAuthResponse;
import com.roadresq.auth.admin.response.AdminResponse;
import com.roadresq.auth.adminActivity.AdminActivityService;
import com.roadresq.auth.userauthmgt.user.UserType;
import com.roadresq.auth.userauthmgt.user.request.PasswordChangeRequest;
import com.roadresq.auth.utils.ApiResponse;
import com.roadresq.auth.utils.PaginatedData;
import com.roadresq.auth.utils.exception.InvalidOperationException;
import com.roadresq.auth.utils.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final AdminActivityService adminActivityService;

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
            adminService.initiateAdminForgotPassword(request);
        } catch (ResourceNotFoundException | InvalidOperationException ignored) {
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
    public ResponseEntity<ApiResponse<AdminResponse>> checkPlatformAccess(
            @RequestAttribute("validatedProfile") AdminResponse profile) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Admin access verified", profile));
    }

    @GetMapping("/private/me")
    public ResponseEntity<AdminResponse> getCurrentAdminProfile() {
        return ResponseEntity.ok(adminService.getCurrentAdminProfile());
    }

    @PostMapping("/private")
    public ResponseEntity<AdminResponse> createAdmin(
            @Valid @RequestBody AdminCreateRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(adminService.createAdmin(request));
    }

    @GetMapping("/private")
    public ResponseEntity<List<AdminResponse>> getAdmins() {
        return ResponseEntity.ok(adminService.getAdmins());
    }

    @GetMapping("/private/{adminId}")
    public ResponseEntity<AdminResponse> getAdminById(
            @PathVariable("adminId") String adminId
    ) {
        return ResponseEntity.ok(adminService.getAdminById(adminId));
    }

    @PutMapping("/private/{adminId}")
    public ResponseEntity<AdminResponse> updateAdmin(
            @PathVariable("adminId") String adminId,
            @Valid @RequestBody AdminUpdateRequest request
    ) {
        return ResponseEntity.ok(
                adminService.updateAdmin(adminId, request)
        );
    }

    @PatchMapping("/private/{adminId}/activate")
    public ResponseEntity<AdminResponse> activateAdmin(
            @PathVariable("adminId") String adminId
    ) {
        return ResponseEntity.ok(adminService.activateAdmin(adminId));
    }

    @PatchMapping("/private/{adminId}/deactivate")
    public ResponseEntity<AdminResponse> deactivateAdmin(
            @PathVariable("adminId") String adminId
    ) {
        return ResponseEntity.ok(adminService.deactivateAdmin(adminId));
    }

    @DeleteMapping("/private/{adminId}")
    public ResponseEntity<Void> deleteAdmin(
            @PathVariable("adminId") String adminId
    ) {
        adminService.deleteAdmin(adminId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/private/count")
    public ResponseEntity<Long> countAllAdmins() {
        return ResponseEntity.ok(adminService.countAllAdmins());
    }

    @GetMapping("/private/count/active")
    public ResponseEntity<Long> countActiveAdmins() {
        return ResponseEntity.ok(adminService.countActiveAdmins());
    }

    @GetMapping("/private/count/inactive")
    public ResponseEntity<Long> countInactiveAdmins() {
        return ResponseEntity.ok(adminService.countInactiveAdmins());
    }

    @GetMapping("/private/portal/analytics")
    public ApiResponse<Map<String, Object>> analytics(@RequestParam(defaultValue = "30") int days) {
        return new ApiResponse<>(true, "Admin analytics retrieved", adminService.getPortalAnalytics(days));
    }

    @GetMapping("/private/portal/summary")
    public ApiResponse<Map<String, Long>> summary() {
        return new ApiResponse<>(true, "Admin summary retrieved", adminService.getPortalSummary());
    }

    @GetMapping("/private/portal/members")
    public ApiResponse<PaginatedData> members(
            @RequestParam UserType role,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String eligibility,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return new ApiResponse<>(true, "Platform members retrieved",
                adminService.getPlatformMembers(role, search, eligibility, page, size));
    }

    @PatchMapping("/private/portal/memberships/{id}")
    public ApiResponse<Void> updateAccess(
            @PathVariable String id,
            @Valid @RequestBody AdminAccessRequest request
    ) {
        adminService.updatePlatformAccess(id, request);
        return new ApiResponse<>(true, "Road ResQ access updated", null);
    }

    @GetMapping("/private/portal/activity")
    public ApiResponse<PaginatedData> activity(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return new ApiResponse<>(true, "Admin activity retrieved",
                adminActivityService.getActivity(page, size));
    }

    @PatchMapping("/private/profile")
    public ApiResponse<AdminResponse> updateProfile(
            @Valid @RequestBody AdminUpdateRequest request
    ) {
        return new ApiResponse<>(true, "Profile updated", adminService.updateMyProfile(request));
    }

    @PatchMapping("/private/change-password")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody PasswordChangeRequest request
    ) {
        adminService.changeMyPassword(request);
        return new ApiResponse<>(true, "Password changed. Sign in again", null);
    }

    @GetMapping("/private/portal/admins")
    public ApiResponse<PaginatedData> adminPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return new ApiResponse<>(true, "Administrators retrieved",
                adminService.getAdminPage(page, size));
    }
}