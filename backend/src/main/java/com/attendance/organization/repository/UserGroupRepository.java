package com.attendance.organization.repository;

import com.attendance.organization.domain.UserGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserGroupRepository extends JpaRepository<UserGroup, UUID> {

    List<UserGroup> findAllByParentIdIsNullOrderByNameAsc();

    List<UserGroup> findAllByParentIdOrderByNameAsc(UUID parentId);

    boolean existsByParentId(UUID parentId);
}
