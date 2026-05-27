package com.attendance.organization.service;

import com.attendance.common.error.ApiException;
import com.attendance.organization.domain.UserGroup;
import com.attendance.organization.repository.UserGroupRepository;
import com.attendance.organization.web.UserGroupDtos;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserGroupService {

    private final UserGroupRepository userGroupRepository;

    public UserGroupService(UserGroupRepository userGroupRepository) {
        this.userGroupRepository = userGroupRepository;
    }

    @Transactional(readOnly = true)
    public List<UserGroupDtos.UserGroupResponse> list() {
        return userGroupRepository.findAll(Sort.by("name"))
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<UserGroupDtos.UserGroupNode> tree() {
        List<UserGroup> all = userGroupRepository.findAll();
        Map<UUID, List<UserGroup>> byParent = new HashMap<>();
        for (UserGroup g : all) {
            byParent.computeIfAbsent(g.getParentId(), k -> new ArrayList<>()).add(g);
        }
        for (List<UserGroup> children : byParent.values()) {
            children.sort(Comparator.comparing(UserGroup::getName, String.CASE_INSENSITIVE_ORDER));
        }
        List<UserGroup> roots = byParent.getOrDefault(null, List.of());
        return roots.stream().map(g -> buildNode(g, byParent)).toList();
    }

    @Transactional(readOnly = true)
    public UserGroupDtos.UserGroupResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public UserGroupDtos.UserGroupResponse create(UserGroupDtos.UserGroupRequest req) {
        validateParent(req.parentId(), null);
        UserGroup g = new UserGroup();
        g.setName(req.name().trim());
        g.setParentId(req.parentId());
        g.setDescription(req.description());
        return toResponse(userGroupRepository.save(g));
    }

    @Transactional
    public UserGroupDtos.UserGroupResponse update(UUID id, UserGroupDtos.UserGroupRequest req) {
        UserGroup g = findOrThrow(id);
        validateParent(req.parentId(), id);
        g.setName(req.name().trim());
        g.setParentId(req.parentId());
        g.setDescription(req.description());
        return toResponse(g);
    }

    @Transactional
    public void delete(UUID id) {
        UserGroup g = findOrThrow(id);
        if (userGroupRepository.existsByParentId(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Group has child groups — reassign or delete them first");
        }
        userGroupRepository.delete(g);
    }

    private void validateParent(UUID parentId, UUID selfId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(selfId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "Group cannot be its own parent");
        }
        UUID cursor = parentId;
        int hops = 0;
        while (cursor != null) {
            if (selfId != null && cursor.equals(selfId)) {
                throw new ApiException(HttpStatus.CONFLICT, "conflict",
                        "Move would create a cycle in the group tree");
            }
            UserGroup parent = userGroupRepository.findById(cursor).orElseThrow(() ->
                    new ApiException(HttpStatus.BAD_REQUEST, "validation",
                            "Parent group not found"));
            cursor = parent.getParentId();
            if (++hops > 64) {
                throw new ApiException(HttpStatus.CONFLICT, "conflict",
                        "Group hierarchy too deep");
            }
        }
    }

    private UserGroupDtos.UserGroupNode buildNode(UserGroup g,
                                                  Map<UUID, List<UserGroup>> byParent) {
        List<UserGroupDtos.UserGroupNode> children = byParent.getOrDefault(g.getId(), List.of())
                .stream().map(c -> buildNode(c, byParent)).toList();
        return new UserGroupDtos.UserGroupNode(g.getId(), g.getName(), g.getDescription(), children);
    }

    private UserGroup findOrThrow(UUID id) {
        return userGroupRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found",
                        "Group not found"));
    }

    private UserGroupDtos.UserGroupResponse toResponse(UserGroup g) {
        return new UserGroupDtos.UserGroupResponse(g.getId(), g.getName(), g.getParentId(),
                g.getDescription(), g.getCreatedAt(), g.getUpdatedAt(), g.getVersion());
    }
}
