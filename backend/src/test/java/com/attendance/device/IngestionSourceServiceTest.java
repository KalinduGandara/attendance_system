package com.attendance.device;

import com.attendance.device.domain.IngestionSourceType;
import com.attendance.device.repository.IngestionSourceRepository;
import com.attendance.device.service.IngestionSourceAuthService;
import com.attendance.device.service.IngestionSourceService;
import com.attendance.device.web.IngestionSourceDtos;
import com.attendance.platform.security.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IngestionSourceServiceTest {

    @Autowired IngestionSourceService service;
    @Autowired IngestionSourceAuthService authService;
    @Autowired IngestionSourceRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void create_returns_plaintext_api_key_only_once_and_stores_hash() {
        var result = service.create(new IngestionSourceDtos.IngestionSourceRequest(
                "Main REST", IngestionSourceType.REST, true, Map.of("region", "us-east")));

        assertThat(result.apiKey()).startsWith("atts_");
        assertThat(result.source().apiKeyConfigured()).isTrue();

        var stored = repository.findById(result.source().id()).orElseThrow();
        assertThat(stored.getApiKeyHash()).isEqualTo(TokenHasher.sha256(result.apiKey()));
        assertThat(stored.getApiKeyHash()).isNotEqualTo(result.apiKey());
    }

    @Test
    void authenticate_resolves_only_with_correct_raw_key() {
        var created = service.create(new IngestionSourceDtos.IngestionSourceRequest(
                "Resolver", IngestionSourceType.REST, true, null));

        assertThat(authService.authenticate(created.apiKey())).isPresent();
        assertThat(authService.authenticate(created.apiKey()).orElseThrow().getId())
                .isEqualTo(created.source().id());

        assertThat(authService.authenticate("wrong-key")).isEmpty();
        assertThat(authService.authenticate("")).isEmpty();
        assertThat(authService.authenticate(null)).isEmpty();
    }

    @Test
    void disabled_source_fails_authentication() {
        var created = service.create(new IngestionSourceDtos.IngestionSourceRequest(
                "Paused", IngestionSourceType.REST, true, null));

        service.update(created.source().id(), new IngestionSourceDtos.IngestionSourceRequest(
                "Paused", IngestionSourceType.REST, false, null));

        assertThat(authService.authenticate(created.apiKey())).isEmpty();
    }

    @Test
    void rotate_key_invalidates_old_key_and_returns_new_plaintext() {
        var created = service.create(new IngestionSourceDtos.IngestionSourceRequest(
                "Rotator", IngestionSourceType.REST, true, null));
        String oldKey = created.apiKey();

        var rotated = service.rotateApiKey(created.source().id());
        assertThat(rotated.apiKey()).isNotEqualTo(oldKey);
        assertThat(rotated.source().id()).isEqualTo(created.source().id());

        assertThat(authService.authenticate(oldKey)).isEmpty();
        assertThat(authService.authenticate(rotated.apiKey())).isPresent();
    }

    @Test
    void list_returns_sources_alphabetically() {
        service.create(new IngestionSourceDtos.IngestionSourceRequest(
                "Bravo", IngestionSourceType.REST, true, null));
        service.create(new IngestionSourceDtos.IngestionSourceRequest(
                "Alpha", IngestionSourceType.REST, true, null));

        assertThat(service.list()).extracting(IngestionSourceDtos.IngestionSourceResponse::name)
                .containsExactly("Alpha", "Bravo");
    }
}
