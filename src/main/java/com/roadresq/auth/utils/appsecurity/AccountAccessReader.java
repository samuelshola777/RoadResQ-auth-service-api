package com.roadresq.auth.utils.appsecurity;

import com.mongodb.client.MongoDatabase;
import io.jsonwebtoken.Claims;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.include;

final class AccountAccessReader {
    enum Result { ALLOWED, UNAUTHENTICATED, FORBIDDEN }

    private AccountAccessReader() {}

    static Result check(MongoDatabase db, Claims claims, ZoneId authZone) {
        String userId = claims.get("userId", String.class);
        String role = claims.get("userType", String.class);
        if (userId == null || userId.isBlank() || claims.getSubject() == null
                || !"ROAD_RESQ".equals(claims.get("servicePlatformType", String.class))) {
            return Result.FORBIDDEN;
        }

        String collection;
        String claim;
        switch (role == null ? "" : role) {
            case "CUSTOMER" -> { collection = "customers"; claim = "customerId"; }
            case "VENDOR" -> { collection = "vendors"; claim = "vendorId"; }
            case "ADMIN" -> { collection = "admin"; claim = "adminId"; }
            default -> { return Result.FORBIDDEN; }
        }

        Document user = db.getCollection("user").find(in("_id", ids(userId)))
                .projection(include("emailAddress", "userType", "isActive", "isVerified",
                        "isDeleted", "passwordChangedAt")).first();
        if (user == null || !role.equals(user.getString("userType"))
                || !claims.getSubject().equalsIgnoreCase(user.getString("emailAddress"))
                || !Boolean.FALSE.equals(user.getBoolean("isDeleted"))) {
            return Result.FORBIDDEN;
        }
        if ("VENDOR".equals(role) && !Boolean.TRUE.equals(user.getBoolean("isVerified"))) {
            return Result.FORBIDDEN;
        }
        if (!Boolean.TRUE.equals(user.getBoolean("isActive"))
                || !Boolean.TRUE.equals(user.getBoolean("isVerified"))) {
            return Result.UNAUTHENTICATED;
        }

        String profileId = claims.get(claim, String.class);
        if (profileId == null || profileId.isBlank()) return Result.FORBIDDEN;
        Document profile = db.getCollection(collection)
                .find(and(in("_id", ids(profileId)), eq("userId", userId), eq("deleted", false)))
                .projection(include("active", "isSuperAdmin")).first();
        if (profile == null) return Result.FORBIDDEN;
        if (!Boolean.TRUE.equals(profile.getBoolean("active"))) return Result.UNAUTHENTICATED;

        if ("ADMIN".equals(role)) {
            Date passwordChanged = user.getDate("passwordChangedAt");
            String passwordVersion = passwordChanged == null ? "INITIAL"
                    : LocalDateTime.ofInstant(passwordChanged.toInstant(), authZone).toString();
            Object authorities = claims.get("auth");
            return passwordVersion.equals(claims.get("passwordVersion", String.class))
                    && authorities instanceof List<?> roles
                    && Boolean.TRUE.equals(profile.getBoolean("isSuperAdmin")) == roles.contains("ROLE_SUPER_ADMIN")
                    ? Result.ALLOWED : Result.FORBIDDEN;
        }

        Document membership = db.getCollection("platform_memberships").find(and(
                eq("userId", userId), eq("userType", role), eq("servicePlatformType", "ROAD_RESQ"),
                eq("isActive", true))).projection(include("_id")).first();
        return membership == null ? Result.FORBIDDEN : Result.ALLOWED;
    }

    private static List<Object> ids(String id) {
        return ObjectId.isValid(id) ? List.of(new ObjectId(id), id) : List.of(id);
    }
}