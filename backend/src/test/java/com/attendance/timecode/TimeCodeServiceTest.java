package com.attendance.timecode;

import com.attendance.common.error.ApiException;
import com.attendance.timecode.domain.TimeCodeCategory;
import com.attendance.timecode.repository.TimeCodeRepository;
import com.attendance.timecode.service.TimeCodeService;
import com.attendance.timecode.web.TimeCodeDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TimeCodeServiceTest {

    @Autowired TimeCodeService service;
    @Autowired TimeCodeRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void create_then_get_round_trip() {
        var created = service.create(new TimeCodeDtos.TimeCodeRequest(
                "REG", "Regular hours", TimeCodeCategory.ATTENDANCE,
                new BigDecimal("1.00"), "#3b82f6", true, true,
                "Standard work", true));
        var fetched = service.get(created.id());
        assertThat(fetched.code()).isEqualTo("REG");
        assertThat(fetched.rate()).isEqualByComparingTo("1.00");
        assertThat(fetched.color()).isEqualTo("#3b82f6");
    }

    @Test
    void duplicate_code_rejected() {
        service.create(new TimeCodeDtos.TimeCodeRequest(
                "REG", "Regular", TimeCodeCategory.ATTENDANCE,
                new BigDecimal("1.00"), "#3b82f6", true, true, null, true));
        assertThatThrownBy(() -> service.create(new TimeCodeDtos.TimeCodeRequest(
                "reg", "Other", TimeCodeCategory.ATTENDANCE,
                new BigDecimal("1.00"), "#3b82f6", true, true, null, true)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void list_filters_by_category_and_active() {
        service.create(new TimeCodeDtos.TimeCodeRequest(
                "REG", "Regular", TimeCodeCategory.ATTENDANCE,
                new BigDecimal("1.00"), "#3b82f6", true, true, null, true));
        service.create(new TimeCodeDtos.TimeCodeRequest(
                "OT-A", "OT", TimeCodeCategory.OVERTIME,
                new BigDecimal("1.50"), "#f59e0b", true, true, null, true));
        service.create(new TimeCodeDtos.TimeCodeRequest(
                "OLD", "Inactive", TimeCodeCategory.ATTENDANCE,
                new BigDecimal("1.00"), "#000000", true, true, null, false));

        assertThat(service.list(null, false)).hasSize(3);
        assertThat(service.list(TimeCodeCategory.OVERTIME, null)).hasSize(1);
        assertThat(service.list(null, true)).hasSize(2);
    }

    @Test
    void update_replaces_fields() {
        var created = service.create(new TimeCodeDtos.TimeCodeRequest(
                "REG", "Regular", TimeCodeCategory.ATTENDANCE,
                new BigDecimal("1.00"), "#3b82f6", true, true, null, true));
        var updated = service.update(created.id(), new TimeCodeDtos.TimeCodeRequest(
                "REG", "Renamed", TimeCodeCategory.ATTENDANCE,
                new BigDecimal("1.25"), "#ff0000", true, true, "note", true));
        assertThat(updated.name()).isEqualTo("Renamed");
        assertThat(updated.rate()).isEqualByComparingTo("1.25");
        assertThat(updated.color()).isEqualTo("#ff0000");
        assertThat(updated.description()).isEqualTo("note");
    }

    @Test
    void get_unknown_throws_not_found() {
        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }
}
