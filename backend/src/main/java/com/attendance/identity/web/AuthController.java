package com.attendance.identity.web;

import com.attendance.common.error.ApiException;
import com.attendance.identity.service.AuthService;
import com.attendance.platform.security.AppPrincipal;
import com.attendance.platform.security.AuthProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final AuthProperties authProperties;

    public AuthController(AuthService authService, AuthProperties authProperties) {
        this.authService = authService;
        this.authProperties = authProperties;
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with username + password")
    public ResponseEntity<AuthDtos.TokenResponse> login(@Valid @RequestBody AuthDtos.LoginRequest body,
                                                       HttpServletRequest req,
                                                       HttpServletResponse res) {
        AuthService.IssuedTokens tokens = authService.login(
                body.username(),
                body.password(),
                req.getHeader("User-Agent"),
                clientIp(req));
        writeRefreshCookie(res, tokens.refreshToken());
        return ResponseEntity.ok(tokens.body());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange refresh cookie for a new access token")
    public ResponseEntity<AuthDtos.TokenResponse> refresh(HttpServletRequest req,
                                                         HttpServletResponse res) {
        String raw = readRefreshCookie(req);
        AuthService.IssuedTokens tokens = authService.refresh(raw,
                req.getHeader("User-Agent"), clientIp(req));
        writeRefreshCookie(res, tokens.refreshToken());
        return ResponseEntity.ok(tokens.body());
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the current refresh-token family")
    public ResponseEntity<Void> logout(HttpServletRequest req, HttpServletResponse res) {
        String raw = readRefreshCookie(req);
        authService.logout(raw);
        clearRefreshCookie(res);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Get the currently authenticated user")
    public AuthDtos.UserInfo me(@AuthenticationPrincipal AppPrincipal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.unauthenticated", "Not authenticated");
        }
        return authService.me(principal);
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change the current user's password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AppPrincipal principal,
                                               @Valid @RequestBody AuthDtos.ChangePasswordRequest body) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.unauthenticated", "Not authenticated");
        }
        authService.changePassword(principal.userId(), body.currentPassword(), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    private String readRefreshCookie(HttpServletRequest req) {
        if (req.getCookies() == null) {
            return null;
        }
        String name = authProperties.cookie().refreshName();
        for (Cookie c : req.getCookies()) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private void writeRefreshCookie(HttpServletResponse res, String value) {
        Cookie cookie = new Cookie(authProperties.cookie().refreshName(), value);
        cookie.setHttpOnly(true);
        cookie.setSecure(authProperties.cookie().secure());
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge((int) authProperties.jwt().refreshTtlSeconds());
        cookie.setAttribute("SameSite", "Strict");
        res.addCookie(cookie);
    }

    private void clearRefreshCookie(HttpServletResponse res) {
        Cookie cookie = new Cookie(authProperties.cookie().refreshName(), "");
        cookie.setHttpOnly(true);
        cookie.setSecure(authProperties.cookie().secure());
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Strict");
        res.addCookie(cookie);
    }

    private String clientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma < 0 ? forwarded.trim() : forwarded.substring(0, comma).trim();
        }
        return req.getRemoteAddr();
    }
}
