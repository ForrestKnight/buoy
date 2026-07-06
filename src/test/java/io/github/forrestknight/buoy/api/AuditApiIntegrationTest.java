package io.github.forrestknight.buoy.api;

import io.github.forrestknight.buoy.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "buoy.bootstrap.username=admin",
        "buoy.bootstrap.password=admin-test-password",
        "buoy.security.jwt-secret=integration-test-secret-0123456789abcdef"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuditApiIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String bearer;

    @BeforeEach
    void login() {
        var result = mvc.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "admin", "password": "admin-test-password"}""")
                .exchange();
        assertThat(result).hasStatusOk();
        try {
            bearer = "Bearer " + objectMapper.readTree(
                    ((org.springframework.mock.web.MockHttpServletResponse) result.getResponse())
                            .getContentAsString()).get("token").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void everyMutationLeavesAnAuditTrail() {
        // A realistic edit session: project → environment → flag → enable in prod
        assertThat(mvc.post().uri("/api/v1/projects")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "audited-project", "name": "Audited"}"""))
                .hasStatus(HttpStatus.CREATED);
        assertThat(mvc.post().uri("/api/v1/projects/audited-project/environments")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "prod", "name": "Production"}"""))
                .hasStatus(HttpStatus.CREATED);
        assertThat(mvc.post().uri("/api/v1/projects/audited-project/flags")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "audited-flag", "name": "Audited flag"}"""))
                .hasStatus(HttpStatus.CREATED);
        assertThat(mvc.put().uri("/api/v1/projects/audited-project/flags/audited-flag/config/prod")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version": 0, "enabled": true, "defaultVariation": true, "offVariation": false, "rules": []}"""))
                .hasStatusOk();

        // Newest first: the config update leads
        assertThat(mvc.get().uri("/api/v1/projects/audited-project/audit")
                .header(HttpHeaders.AUTHORIZATION, bearer))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.totalElements", total -> total.assertThat().isEqualTo(4))
                .hasPathSatisfying("$.entries[0].entityType", type -> type.assertThat().isEqualTo("FLAG_CONFIG"))
                .hasPathSatisfying("$.entries[0].actorType", type -> type.assertThat().isEqualTo("USER"))
                .hasPathSatisfying("$.entries[0].actorName", name -> name.assertThat().isEqualTo("admin"))
                .hasPathSatisfying("$.entries[0].diff.before.enabled", v -> v.assertThat().isEqualTo(false))
                .hasPathSatisfying("$.entries[0].diff.after.enabled", v -> v.assertThat().isEqualTo(true))
                .hasPathSatisfying("$.entries[0].source", source ->
                        source.assertThat().asString().startsWith("PUT /api/v1/projects/audited-project"));

        // Filter by entityType
        assertThat(mvc.get().uri("/api/v1/projects/audited-project/audit?entityType=FLAG")
                .header(HttpHeaders.AUTHORIZATION, bearer))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.totalElements", total -> total.assertThat().isEqualTo(1))
                .hasPathSatisfying("$.entries[0].entityKey", key -> key.assertThat().isEqualTo("audited-flag"));

        // Filter by action
        assertThat(mvc.get().uri("/api/v1/projects/audited-project/audit?action=UPDATED")
                .header(HttpHeaders.AUTHORIZATION, bearer))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.totalElements", total -> total.assertThat().isEqualTo(1));
    }
}
