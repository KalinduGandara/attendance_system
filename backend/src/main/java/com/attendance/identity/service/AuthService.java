package com.attendance.identity.service;

import com.attendance.common.error.ApiException;
import com.attendance.common.uuid.UuidV7;
import com.attendance.identity.domain.Permission;
import com.attendance.identity.domain.RefreshToken;
import com.attendance.identity.domain.Role;
import com.attendance.identity.domain.User;
import com.attendance.identity.domain.UserStatus;
import com.attendance.identity.repository.RefreshTokenRepository;
import com.attendance.identity.repository.UserRepository;
import com.attendance.identity.web.AuthDtos;
import com.attendance.platform.security.AppPrincipal;
import com.attendance.platform.security.AuthProperties;
import com.attendance.platform.security.JwtService;
import com.attendance.platform.security.TokenHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthProperties authProperties;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthProperties authProperties) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authProperties = authProperties;
    }

    public record IssuedTokens(AuthDtos.TokenResponse body, String refreshToken) {
    }

    @Transactional
    public IssuedTokens login(String username, String rawPassword, String userAgent, String ip) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "auth.invalid",
                        "Invalid username or password"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.inactive",
                    "Account is not active");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.locked",
                    "Account is temporarily locked");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            user.setFailedLoginCount(user.getFailedLoginCount() + 1);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.invalid",
                    "Invalid username or password");
        }

        user.setFailedLoginCount(0);
        user.setLastLoginAt(Instant.now());

        UUID familyId = UuidV7.generate();
        return issue(user, familyId, userAgent, ip);
    }

    @Transactional
    public IssuedTokens refresh(String rawRefreshToken, String userAgent, String ip) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.invalid", "Missing refresh token");
        }
        String hash = TokenHasher.sha256(rawRefreshToken);
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(hash);
        if (tokenOpt.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.invalid", "Invalid refresh token");
        }
        RefreshToken token = tokenOpt.get();
        Instant now = Instant.now();

        if (token.getRevokedAt() != null) {
            log.warn("Refresh token reuse detected for family {} — invalidating family",
                    token.getFamilyId());
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.invalid",
                    "Refresh token has been revoked");
        }
        if (!token.isActive(now)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.expired", "Refresh token expired");
        }

        token.setRevokedAt(now);
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "auth.invalid",
                        "User not found"));

        return issue(user, token.getFamilyId(), userAgent, ip);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String hash = TokenHasher.sha256(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token ->
                refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now()));
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "user.not-found", "User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "auth.current-password",
                    "Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "auth.same-password",
                    "New password must differ from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserInfo me(AppPrincipal principal) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "user.not-found", "User not found"));
        return toUserInfo(user);
    }

    private IssuedTokens issue(User user, UUID familyId, String userAgent, String ip) {
        List<String> permissions = collectPermissions(user);
        String access = jwtService.issueAccessToken(user.getId(), user.getUsername(), permissions);

        String refreshRaw = generateRefreshTokenValue();
        RefreshToken refresh = new RefreshToken();
        refresh.setUserId(user.getId());
        refresh.setFamilyId(familyId);
        refresh.setTokenHash(TokenHasher.sha256(refreshRaw));
        refresh.setExpiresAt(Instant.now().plusSeconds(authProperties.jwt().refreshTtlSeconds()));
        refresh.setUserAgent(userAgent);
        refresh.setIp(ip);
        refreshTokenRepository.save(refresh);

        AuthDtos.UserInfo info = toUserInfo(user);
        AuthDtos.TokenResponse body = new AuthDtos.TokenResponse(
                access, "Bearer", jwtService.accessTtlSeconds(), info);
        return new IssuedTokens(body, refreshRaw);
    }

    private List<String> collectPermissions(User user) {
        return user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getCode)
                .distinct()
                .sorted()
                .toList();
    }

    private AuthDtos.UserInfo toUserInfo(User user) {
        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .sorted(Comparator.naturalOrder())
                .toList();
        return new AuthDtos.UserInfo(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                roles,
                collectPermissions(user)
        );
    }

    private String generateRefreshTokenValue() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
