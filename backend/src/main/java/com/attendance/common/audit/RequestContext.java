package com.attendance.common.audit;

import com.attendance.platform.security.AppPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

public final class RequestContext {

    private RequestContext() {
    }

    public static Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attr) {
            return Optional.ofNullable(attr.getRequest());
        }
        return Optional.empty();
    }

    public static Optional<AppPrincipal> currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    public static String clientIp() {
        return currentRequest().map(req -> {
            String fwd = req.getHeader("X-Forwarded-For");
            if (fwd != null && !fwd.isBlank()) {
                int comma = fwd.indexOf(',');
                return comma < 0 ? fwd.trim() : fwd.substring(0, comma).trim();
            }
            return req.getRemoteAddr();
        }).orElse(null);
    }

    public static String userAgent() {
        return currentRequest().map(r -> r.getHeader("User-Agent")).orElse(null);
    }

    public static String requestId() {
        return currentRequest().map(r -> r.getHeader("X-Request-Id")).orElse(null);
    }

    public static Optional<UUID> actorUserId() {
        return currentPrincipal().map(AppPrincipal::userId);
    }

    public static String actorUsername() {
        return currentPrincipal().map(AppPrincipal::username).orElse("system");
    }
}
