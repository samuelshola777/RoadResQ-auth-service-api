package com.roadresq.auth.utils.appsecurity;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Component
public class DefaultRouteValidator {

    private static final Logger log = LoggerFactory.getLogger(DefaultRouteValidator.class);

    private static Pattern route(String routePrefix) {
        return Pattern.compile("^" + Pattern.quote(routePrefix) + "(?:/.*)?$");
    }

    private static List<Pattern> routes(String... routePrefixes) {
        return Arrays.stream(routePrefixes)
                .distinct()
                .map(DefaultRouteValidator::route)
                .toList();
    }



    public static final List<Pattern> ADMIN_ENDPOINTS = routes(
            "/api/v1/product/private",
            "/api/v1/admin/private",
            "/api/v1/customer/private/admin",
            "/api/v1/vendor/private/admin",
            "/api/v1/files/private",
            "/api/v1/teams/private",
            "/api/v1/players/private",
            "/api/v1/player-stats/private",
            "/api/v1/gallery/private",
            "/api/v1/posts/private",
            "/api/v1/competitions/private",
            "/api/v1/competitions/private/admin",
            "/api/v1/activity-logs",
            "/api/v1/activity-logs/private",
            "/api/v1/activity-logs/private/admin",
            "/api/v1/dashboard/private",
            "/api/v1/dashboard/private/admin",
            "/api/v1/dashboard"
    );


    public DefaultRouteValidator() {
        log.info(
                "[DRV-INIT] Admin patterns={}, Nominee patterns={}, Buyer patterns={}",
                ADMIN_ENDPOINTS.size()
        );
    }

    public Predicate<HttpServletRequest> isCustomerSecured = req -> {
        String uri = req.getRequestURI();
        return (uri.equals("/api/v1/customer/private") || uri.startsWith("/api/v1/customer/private/"))
                && !uri.equals("/api/v1/customer/private/admin")
                && !uri.startsWith("/api/v1/customer/private/admin/");
    };

    public Predicate<HttpServletRequest> isVendorSecured = req -> {
        String uri = req.getRequestURI();
        return (uri.equals("/api/v1/vendor/private")
                || uri.startsWith("/api/v1/vendor/private/"))
                && !uri.equals("/api/v1/vendor/private/admin")
                && !uri.startsWith("/api/v1/vendor/private/admin/");
    };

    public Predicate<HttpServletRequest> isAdminSecured = req ->
            matches(req, ADMIN_ENDPOINTS, "admin");



    private boolean matches(HttpServletRequest request, List<Pattern> patterns, String groupName) {
        String uri = request.getRequestURI();

        boolean matched = patterns.stream()
                .anyMatch(pattern -> pattern.matcher(uri).matches());

        return matched;
    }
}