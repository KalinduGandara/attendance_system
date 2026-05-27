package com.attendance.organization;

import com.attendance.common.error.ApiException;
import com.attendance.organization.repository.UserGroupRepository;
import com.attendance.organization.service.UserGroupService;
import com.attendance.organization.web.UserGroupDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserGroupServiceTest {

    @Autowired
    UserGroupService userGroupService;

    @Autowired
    UserGroupRepository userGroupRepository;

    @BeforeEach
    void clear() {
        userGroupRepository.deleteAllInBatch();
    }

    @Test
    void tree_returns_nested_children() {
        var root = userGroupService.create(new UserGroupDtos.UserGroupRequest("All", null, null));
        var east = userGroupService.create(new UserGroupDtos.UserGroupRequest("East", root.id(), null));
        var west = userGroupService.create(new UserGroupDtos.UserGroupRequest("West", root.id(), null));
        var westA = userGroupService.create(new UserGroupDtos.UserGroupRequest("West-A", west.id(), null));

        var tree = userGroupService.tree();
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).children()).extracting(UserGroupDtos.UserGroupNode::name)
                .containsExactly("East", "West");
        var westNode = tree.get(0).children().stream()
                .filter(n -> n.id().equals(west.id())).findFirst().orElseThrow();
        assertThat(westNode.children()).extracting(UserGroupDtos.UserGroupNode::id)
                .containsExactly(westA.id());
        assertThat(east).isNotNull();
    }

    @Test
    void self_parent_is_rejected() {
        var g = userGroupService.create(new UserGroupDtos.UserGroupRequest("A", null, null));
        assertThatThrownBy(() -> userGroupService.update(g.id(),
                new UserGroupDtos.UserGroupRequest("A", g.id(), null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("own parent");
    }
}
