package com.attendance.organization;

import com.attendance.common.error.ApiException;
import com.attendance.organization.repository.DepartmentRepository;
import com.attendance.organization.service.DepartmentService;
import com.attendance.organization.web.DepartmentDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DepartmentServiceTest {

    @Autowired
    DepartmentService departmentService;

    @Autowired
    DepartmentRepository departmentRepository;

    @BeforeEach
    void clear() {
        departmentRepository.deleteAllInBatch();
    }

    @Test
    void create_then_get_returns_persisted_fields() {
        var created = departmentService.create(new DepartmentDtos.DepartmentRequest(
                "Engineering", null, "Asia/Colombo"));
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("Engineering");
        assertThat(created.timezone()).isEqualTo("Asia/Colombo");

        var fetched = departmentService.get(created.id());
        assertThat(fetched.name()).isEqualTo("Engineering");
    }

    @Test
    void tree_groups_children_under_parents() {
        var eng = departmentService.create(new DepartmentDtos.DepartmentRequest("Engineering", null, null));
        var backend = departmentService.create(new DepartmentDtos.DepartmentRequest("Backend", eng.id(), null));
        var frontend = departmentService.create(new DepartmentDtos.DepartmentRequest("Frontend", eng.id(), null));
        var infra = departmentService.create(new DepartmentDtos.DepartmentRequest("Infra", backend.id(), null));

        List<DepartmentDtos.DepartmentNode> tree = departmentService.tree();
        assertThat(tree).hasSize(1);
        DepartmentDtos.DepartmentNode root = tree.get(0);
        assertThat(root.id()).isEqualTo(eng.id());
        assertThat(root.children()).extracting(DepartmentDtos.DepartmentNode::name)
                .containsExactly("Backend", "Frontend");
        var backendNode = root.children().stream()
                .filter(n -> n.id().equals(backend.id())).findFirst().orElseThrow();
        assertThat(backendNode.children()).extracting(DepartmentDtos.DepartmentNode::id)
                .containsExactly(infra.id());
        assertThat(frontend).isNotNull();
    }

    @Test
    void update_rejects_cycle() {
        var a = departmentService.create(new DepartmentDtos.DepartmentRequest("A", null, null));
        var b = departmentService.create(new DepartmentDtos.DepartmentRequest("B", a.id(), null));

        assertThatThrownBy(() -> departmentService.update(a.id(),
                new DepartmentDtos.DepartmentRequest("A", b.id(), null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void delete_with_children_is_rejected() {
        var parent = departmentService.create(new DepartmentDtos.DepartmentRequest("P", null, null));
        departmentService.create(new DepartmentDtos.DepartmentRequest("C", parent.id(), null));

        assertThatThrownBy(() -> departmentService.delete(parent.id()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("child");
    }

    @Test
    void delete_missing_throws_not_found() {
        assertThatThrownBy(() -> departmentService.delete(UUID.randomUUID()))
                .isInstanceOf(ApiException.class);
    }
}
