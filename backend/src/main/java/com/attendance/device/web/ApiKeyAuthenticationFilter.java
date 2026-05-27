package com.attendance.device.web;

import com.attendance.device.domain.IngestionSource;
import com.attendance.device.service.IngestionSourceAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Authenticates requests bearing an {@code X-Source-Api-Key} header against the
 * {@code ingestion_source} table. On success the security context is populated
 * with an {@link IngestionSourcePrincipal} granting only {@code ingestion.write}.
 *
 * <p>This filter runs before {@link com.attendance.platform.security.JwtAuthenticationFilter}
 * so API-key auth wins over any user JWT also present on the request.
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Source-Api-Key";

    private final IngestionSourceAuthService authService;

    public ApiKeyAuthenticationFilter(IngestionSourceAuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key != null && !key.isBlank()
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<IngestionSource> source = authService.authenticate(key);
            source.ifPresent(s -> {
                IngestionSourcePrincipal principal = new IngestionSourcePrincipal(
                        s.getId(), s.getName(), s.getSourceType());
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        chain.doFilter(request, response);
    }
}
