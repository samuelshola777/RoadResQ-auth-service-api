package com.roadresq.auth.adminActivity;

import com.roadresq.auth.utils.PaginatedData;
import com.roadresq.auth.utils.PaginationService;
import com.roadresq.auth.utils.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminActivityService {
    private final AdminActivityRepository repository;
    private final PaginationService paginationService;
    private final HttpServletRequest request;

    public void record(String action, String targetId, String reason, String before, String after) {
        Object value = request.getAttribute("validatedClaims");
        if (!(value instanceof Claims claims) || !"ADMIN".equals(claims.get("userType", String.class))) {
            throw new UnauthorizedException("Admin authentication is required");
        }
        AdminActivity activity = new AdminActivity();
        activity.setSource("AUTH");
        activity.setActorId(claims.get("adminId", String.class));
        activity.setServicePlatformType(claims.get("servicePlatformType", String.class));
        activity.setAction(action);
        activity.setTargetId(targetId);
        activity.setReason(reason);
        activity.setBeforeState(before);
        activity.setAfterState(after);
        activity.setCreatedAt(LocalDateTime.now());
        repository.insert(activity);
        log.info("Admin activity recorded: action={}, target={}", action, targetId);
    }

    public PaginatedData getActivity(int page, int size) {
        return paginationService.paginate(new Query(Criteria.where("servicePlatformType")
                .is("ROAD_RESQ").and("source").is("AUTH")), AdminActivity.class, page, size, "createdAt", "DESC", Set.of("createdAt"));
    }
}