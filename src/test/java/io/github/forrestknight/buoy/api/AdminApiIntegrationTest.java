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

/**
 * Full-stack CRUD tests, authenticated as the bootstrapped instance admin.
 */
@SpringBootTest(properties = {
        "buoy.bootstrap.username=admin",
        "buoy.bootstrap.password=admin-test-password",
        "buoy.security.jwt-secret=integration-test-secret-0123456789abcdef"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminApiIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String bearer;

    @BeforeEach
    void login() throws Exception {
        var result = mvc.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "admin", "password": "admin-test-password"}""")
                .exchange();
        assertThat(result).hasStatusOk();
        bearer = "Bearer " + objectMapper
                .readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private void createProject(String key) {
        assertThat(mvc.post().uri("/api/v1/projects")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "%s", "name": "Project %s"}""".formatted(key, key)))
                .hasStatus(HttpStatus.CREATED);
    }

    private void createEnvironment(String projectKey, String envKey) {
        assertThat(mvc.post().uri("/api/v1/projects/{p}/environments", projectKey)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "%s", "name": "%s"}""".formatted(envKey, envKey)))
                .hasStatus(HttpStatus.CREATED);
    }

    private void createFlag(String projectKey, String flagKey) {
        assertThat(mvc.post().uri("/api/v1/projects/{p}/flags", projectKey)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "%s", "name": "Flag %s", "tags": ["test"]}""".formatted(flagKey, flagKey)))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void projectCrudRoundTrip() {
        createProject("crud-project");

        assertThat(mvc.get().uri("/api/v1/projects/crud-project")
                .header(HttpHeaders.AUTHORIZATION, bearer))
                .hasStatusOk()
                .bodyJson().extractingPath("$.name").isEqualTo("Project crud-project");

        assertThat(mvc.put().uri("/api/v1/projects/crud-project")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Renamed", "description": "now with description"}"""))
                .hasStatusOk()
                .bodyJson().extractingPath("$.description").isEqualTo("now with description");

        assertThat(mvc.delete().uri("/api/v1/projects/crud-project")
                .header(HttpHeaders.AUTHORIZATION, bearer))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(mvc.get().uri("/api/v1/projects/crud-project")
                .header(HttpHeaders.AUTHORIZATION, bearer))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.title").isEqualTo("Resource not found");
    }

    @Test
    void duplicateProjectKeyIsConflict() {
        createProject("dupe-project");
        assertThat(mvc.post().uri("/api/v1/projects")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "dupe-project", "name": "Again"}"""))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.title").isEqualTo("Duplicate key");
    }

    @Test
    void malformedKeyIsRejectedWithFieldErrors() {
        assertThat(mvc.post().uri("/api/v1/projects")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "Not_Kebab", "name": "Bad"}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.fieldErrors.key").asString().contains("kebab-case");
    }

    @Test
    void creatingFlagBackfillsDisabledConfigInEveryEnvironment() {
        createProject("backfill-project");
        createEnvironment("backfill-project", "dev");
        createEnvironment("backfill-project", "prod");
        createFlag("backfill-project", "checkout-redesign");

        assertThat(mvc.get().uri("/api/v1/projects/backfill-project/flags/checkout-redesign/config/prod")
                .header(HttpHeaders.AUTHORIZATION, bearer))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.enabled", enabled -> enabled.assertThat().isEqualTo(false))
                .hasPathSatisfying("$.version", version -> version.assertThat().isEqualTo(0));
    }

    @Test
    void creatingEnvironmentBackfillsConfigsForExistingFlags() {
        createProject("late-env-project");
        createFlag("late-env-project", "existing-flag");
        createEnvironment("late-env-project", "staging");

        assertThat(mvc.get().uri("/api/v1/projects/late-env-project/flags/existing-flag/config/staging")
                .header(HttpHeaders.AUTHORIZATION, bearer))
                .hasStatusOk();
    }

    @Test
    void configUpdateWithRulesAndVersionConflict() {
        createProject("rules-project");
        createEnvironment("rules-project", "prod");
        assertThat(mvc.post().uri("/api/v1/projects/rules-project/segments")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "beta-testers", "name": "Beta testers",
                         "clauses": [{"attribute": "email", "operator": "ends_with", "values": ["@acme.test"]}]}"""))
                .hasStatus(HttpStatus.CREATED);
        createFlag("rules-project", "new-payment-flow");

        String update = """
                {"version": 0, "enabled": true, "defaultVariation": false, "offVariation": false,
                 "rules": [
                   {"clauses": [{"operator": "in_segment", "values": ["beta-testers"]}],
                    "rollout": {"type": "FIXED", "variation": true}},
                   {"clauses": [{"attribute": "plan", "operator": "equals", "values": ["enterprise"]}],
                    "rollout": {"type": "PERCENTAGE", "weights": [
                      {"variation": true, "weightThousandths": 25000},
                      {"variation": false, "weightThousandths": 75000}]}}
                 ]}""";

        assertThat(mvc.put().uri("/api/v1/projects/rules-project/flags/new-payment-flow/config/prod")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(update))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.version", version -> version.assertThat().isEqualTo(1))
                .hasPathSatisfying("$.rules[0].id", id -> id.assertThat().asString().isNotBlank());

        // Same request again still claims version 0 → stale → 409
        assertThat(mvc.put().uri("/api/v1/projects/rules-project/flags/new-payment-flow/config/prod")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(update))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.title").isEqualTo("Version conflict");
    }

    @Test
    void semanticRuleValidationFailures() {
        createProject("invalid-rules-project");
        createEnvironment("invalid-rules-project", "prod");
        createFlag("invalid-rules-project", "some-flag");

        // Weights don't sum to 100_000
        assertThat(mvc.put().uri("/api/v1/projects/invalid-rules-project/flags/some-flag/config/prod")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version": 0, "enabled": true, "defaultVariation": true, "offVariation": false,
                         "rules": [{"clauses": [{"attribute": "plan", "operator": "equals", "values": ["pro"]}],
                                    "rollout": {"type": "PERCENTAGE", "weights": [
                                      {"variation": true, "weightThousandths": 10000}]}}]}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").asString().contains("sum to 100000");

        // Rule references a segment that doesn't exist
        assertThat(mvc.put().uri("/api/v1/projects/invalid-rules-project/flags/some-flag/config/prod")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version": 0, "enabled": true, "defaultVariation": true, "offVariation": false,
                         "rules": [{"clauses": [{"operator": "in_segment", "values": ["ghosts"]}],
                                    "rollout": {"type": "FIXED", "variation": true}}]}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").asString().contains("Unknown segment 'ghosts'");

        // Segments cannot nest segments
        assertThat(mvc.post().uri("/api/v1/projects/invalid-rules-project/segments")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "nested", "name": "Nested",
                         "clauses": [{"operator": "in_segment", "values": ["beta-testers"]}]}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").asString().contains("cannot reference other segments");
    }
}
