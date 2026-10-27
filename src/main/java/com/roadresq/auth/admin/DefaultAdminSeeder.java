package com.roadresq.auth.admin;

import com.roadresq.auth.userauthmgt.user.User;
import com.roadresq.auth.userauthmgt.user.UserService;
import com.roadresq.auth.utils.Gender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAdminSeeder {

    private static final String DEFAULT_ADMIN_EMAIL = "drive.relay14@gmail.com";
    private static final String DEFAULT_ADMIN_PASSWORD = "password";

    private final AdminRepository adminRepository;
    private final UserService userService;

    @EventListener(ApplicationReadyEvent.class)
    public void seedDefaultSuperAdmin() {
        try {
            boolean superAdminExists = adminRepository.existsByIsSuperAdmin(true);

            if (superAdminExists) {
                log.info("✅ Admin already exists");
                return;
            }

            boolean emailExists = userService.existsByEmailAddressIgnoreCase(DEFAULT_ADMIN_EMAIL);

            if (emailExists) {
                log.info("✅ Admin already exists");
                return;
            }

            User user = userService.createAdminUserFromAdminModule(
                    "Healthee",
                    "FruitJuices",
                    DEFAULT_ADMIN_EMAIL,
                    DEFAULT_ADMIN_PASSWORD,
                    null,
                    null,
                    "#0B5FFF",
                    Gender.MALE,
                    true
            );

            Admin admin = Admin.builder()
                    .userId(user.getId())
                    .active(true)
                    .deleted(false)
                    .lastLoginAt(null)
                    .deletedAt(null)
                    .build();

            admin.setCreatedAt(LocalDateTime.now());
            admin.setUpdatedAt(LocalDateTime.now());

            adminRepository.save(admin);

            log.info("✅ Default super admin created successfully. Email: {}", DEFAULT_ADMIN_EMAIL);

        } catch (Exception exception) {
            log.error("❌ Failed to seed default super admin", exception);
        }
    }
}