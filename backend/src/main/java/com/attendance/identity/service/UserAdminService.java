package com.attendance.identity.service;

import com.attendance.common.error.ApiException;
import com.attendance.common.web.PageResponse;
import com.attendance.identity.domain.Role;
import com.attendance.identity.domain.User;
import com.attendance.identity.domain.UserStatus;
import com.attendance.identity.repository.RefreshTokenRepository;
import com.attendance.identity.repository.RoleRepository;
import com.attendance.identity.repository.UserRepository;
import com.attendance.identity.web.UserAdminDtos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;

    public UserAdminService(UserRepository userRepository,
                            RoleRepository roleRepository,
                            PasswordEncoder passwordEncoder,
                            RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserAdminDtos.UserResponse> list(int page, int size, String sortField) {
        Sort sort = Sort.by(switch (sortField == null ? "username" : sortField) {
            case "email", "createdAt", "username" -> sortField;
            default -> "username";
        });
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), sort);
        Page<User> users = userRepository.findAll(pageable);
        return PageResponse.of(users, this::toResponse);
    }

    @Transactional(readOnly = true)
    public UserAdminDtos.UserResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public UserAdminDtos.UserResponse create(UserAdminDtos.CreateUserRequest req) {
        if (userRepository.existsByUsernameIgnoreCase(req.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict", "Username already in use");
        }
        Set<Role> roles = resolveRoles(req.roleIds());
        User u = new User();
        u.setUsername(req.username().trim());
        u.setEmail(req.email().trim());
        u.setDisplayName(req.displayName());
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setStatus(req.status() == null ? UserStatus.ACTIVE : req.status());
        u.setRoles(roles);
        return toResponse(userRepository.save(u));
    }

    @Transactional
    public UserAdminDtos.UserResponse update(UUID id, UserAdminDtos.UpdateUserRequest req) {
        User u = findOrThrow(id);
        if (!u.getUsername().equalsIgnoreCase(req.username())
                && userRepository.existsByUsernameIgnoreCase(req.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict", "Username already in use");
        }
        Set<Role> roles = resolveRoles(req.roleIds());
        boolean wasActive = u.getStatus() == UserStatus.ACTIVE;
        u.setUsername(req.username().trim());
        u.setEmail(req.email().trim());
        u.setDisplayName(req.displayName());
        if (req.status() != null) {
            u.setStatus(req.status());
        }
        u.setRoles(roles);
        if (wasActive && u.getStatus() != UserStatus.ACTIVE) {
            refreshTokenRepository.revokeAllForUser(u.getId(), Instant.now());
        }
        return toResponse(u);
    }

    @Transactional
    public void resetPassword(UUID id, String newPassword) {
        User u = findOrThrow(id);
        u.setPasswordHash(passwordEncoder.encode(newPassword));
        refreshTokenRepository.revokeAllForUser(u.getId(), Instant.now());
    }

    @Transactional
    public void delete(UUID id) {
        User u = findOrThrow(id);
        Set<String> systemRoles = Set.of("ADMIN");
        boolean isSystemAdmin = u.getRoles().stream()
                .anyMatch(r -> systemRoles.contains(r.getName()));
        if (isSystemAdmin) {
            long admins = userRepository.findAll().stream()
                    .filter(other -> other.getRoles().stream().anyMatch(r -> r.getName().equals("ADMIN")))
                    .count();
            if (admins <= 1) {
                throw new ApiException(HttpStatus.CONFLICT, "conflict",
                        "Cannot delete the last ADMIN user");
            }
        }
        refreshTokenRepository.revokeAllForUser(u.getId(), Instant.now());
        userRepository.delete(u);
    }

    private Set<Role> resolveRoles(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "User must have at least one role");
        }
        List<Role> roles = roleRepository.findAllById(ids);
        if (roles.size() != ids.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "One or more roles not found");
        }
        return new HashSet<>(roles);
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found",
                        "User not found"));
    }

    private UserAdminDtos.UserResponse toResponse(User u) {
        List<UserAdminDtos.RoleRef> roleRefs = u.getRoles().stream()
                .sorted(Comparator.comparing(Role::getName))
                .map(r -> new UserAdminDtos.RoleRef(r.getId(), r.getName()))
                .toList();
        return new UserAdminDtos.UserResponse(
                u.getId(), u.getUsername(), u.getEmail(), u.getDisplayName(),
                u.getStatus(), roleRefs, u.getLastLoginAt(),
                u.getCreatedAt(), u.getUpdatedAt(), u.getVersion());
    }
}
