package com.roadresq.auth.utils.appsecurity;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Component
public class RouteValidator {

    private static Pattern prefix(String path) {
        return Pattern.compile("^" + Pattern.quote(path) + "(?:/.*)?$");
    }

    private static List<Pattern> prefixes(String... paths) {
        return java.util.Arrays.stream(paths).map(RouteValidator::prefix).toList();
    }

    public static final List<Pattern> OPEN_API_ENDPOINTS = prefixes(
            "/api/v1/customer/public",
            "/api/v1/vendor/public",
            "/api/v1/product/public",
            "/api/v1/admin/public",
            "/api/v1/utilities/public",
            "/api/v1/system/public",
            "/ws",
            "/ws-native",
            "/ws-sockjs"
    );

    private static final List<Pattern> DOCS_AND_TOOLS = List.of(
            Pattern.compile("^/magic/public(?:/.*)?$"),
            Pattern.compile("^/swagger-ui\\.html$"),
            Pattern.compile("^/swagger-ui(?:/.*)?$"),
            Pattern.compile("^/v3/api-docs(?:/.*)?$"),
            Pattern.compile("^/v3/api-docs\\.yaml$"),
            Pattern.compile("^/api-docs(?:/.*)?$")
    );

    private static final List<Pattern> ALL_OPEN = java.util.stream.Stream
            .concat(OPEN_API_ENDPOINTS.stream(), DOCS_AND_TOOLS.stream())
            .toList();

    public final Predicate<HttpServletRequest> isPublic = request -> {
        String uri = request.getRequestURI();
        return ALL_OPEN.stream().anyMatch(pattern -> pattern.matcher(uri).matches());
    };

    public final Predicate<HttpServletRequest> isSecured = isPublic.negate();
}