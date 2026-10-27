package com.roadresq.auth.utils.appsecurity;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.roadresq.auth.utils.exception.InvalidOperationException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RequiredArgsConstructor
@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

    private final JwtService jwtService;                    
    private final DefaultRouteValidator routeValidator;
    private final RouteValidator publicRouteValidator;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        log.info("doFilterInternal START - req={} {}", req.getMethod(), req.getRequestURI());

        if(req.getRequestURI().contains("ws") || req.getRequestURI().contains("ws-native") || req.getRequestURI().contains("ws-sockjs")) {
            log.info("Websocket request detected, continuing without auth - uri={}", req.getRequestURI());
            chain.doFilter(req, res);
            log.info("Returned from chain for websocket request - uri={}", req.getRequestURI());
            return;
        }

        // allow public routes early
        if (req.getRequestURI().contains("magik/public/") || publicRouteValidator.isPublic.test(req) || req.getRequestURI().contains("/api/v1/utilities/public/")) {
            log.info("PUBLIC route detected, continuing without auth - uri={}", req.getRequestURI());
            chain.doFilter(req, res);
            log.info("Returned from chain for public route - uri={}", req.getRequestURI());
            return;
        }

        String token = resolveToken(req);
        if (token == null) { // no token → unauth for secured routes
            log.warn("Missing token for secured route - uri={}", req.getRequestURI());
            forbid(res, "Missing token.......");
            return;
        }

        Claims claims;
        try {
            log.info("Parsing token (masked) - tokenPreview={}", mask(token));
            claims = jwtService.parse(token);             
            log.info("Token parsed successfully");
        } catch (Exception e) {
            log.warn("Invalid token caught: {} - msg={}", e.getClass().getSimpleName(), e.getMessage());
            forbid(res, "Invalid token");
            return;
        }

        String userType = claims.get("userType", String.class);
        List<String> auth = claims.get("auth", List.class);   
        log.info("Claims extracted: userType={} authCount={}", userType, (auth == null ? 0 : auth.size()));
        if (userType == null) {
            log.warn("Missing userType in claims");
            forbid(res, "Missing userType");
            return;
        }

        // route access check by userType
        log.info("Checking route access for userType={} uri={}", userType, req.getRequestURI());
        boolean allowed = switch (userType) {
            case "ADMIN"   -> routeValidator.isAdminSecured.test(req);

            default        -> false;
        };
        log.info("Access allowed={}", allowed);
        if (!allowed) {
            log.warn("Account Unauthorized for userType={} uri={}", userType, req.getRequestURI());
            forbid(res, "Account Unauthorized");
            throw new InvalidOperationException("Account Unauthorized");
        }

        // set SecurityContext so @PreAuthorize works
        var authorities = (auth == null ? List.<GrantedAuthority>of()
                : auth.stream().map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).collect(Collectors.toSet()));
        log.info("Authorities prepared: {}", authorities);
        var authentication = new UsernamePasswordAuthenticationToken(
                claims.get("email", String.class), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.info("SecurityContext set for email={}", claims.get("email", String.class));

        chain.doFilter(req, res);
        log.info("doFilterInternal END - completed filter chain for uri={}", req.getRequestURI());
    }

    private String resolveToken(HttpServletRequest req) {
        boolean headerPresent = req.getHeader("Authorization") != null;
        log.info("resolveToken - header Authorization present? {}", headerPresent);
        String h = req.getHeader("Authorization");
        String token = (h != null && h.startsWith("Bearer ")) ? h.substring(7) : null;
        log.info("resolveToken - tokenPresent={} tokenPreview={}", (token != null), (token == null ? "null" : mask(token)));
        return token;
    }

    private void forbid(HttpServletResponse res, String msg) throws IOException {
        log.warn("forbid - msg={}", msg);
        res.setStatus(HttpStatus.FORBIDDEN.value());
        res.setContentType(APPLICATION_JSON_VALUE);
        new ObjectMapper().writeValue(res.getOutputStream(),
                new ApiResponse(false, msg, null));
        log.info("forbid END - response written with msg={}", msg);
    }

    // helper to avoid printing whole sensitive token
    private static String mask(String token) {
        if (token == null) return "null";
        int keep = Math.min(6, token.length());
        return token.substring(0, keep) + "..." + token.length();
    }
}
