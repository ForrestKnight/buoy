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

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The SDK evaluation surface end-to-end: SDK-key auth, reasons, defaults,
 * bulk evaluation, and cache invalidation on admin mutations.
 */
@SpringBootTest(properties = {
        "buoy.bootstrap.username=admin",
        "buoy.bootstrap.password=admin-test-password",
        "buoy.security.jwt-secret=integration-test-secret-0123456789abcdef"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EvaluationApiIntegrationTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String bearer;
    private String projectKey;
    private String sdkToken;

    @BeforeEach
    void setUpProjectWithSdkKey() {
        bearer = "Bearer " + loginAsAdmin();
        projectKey = "eval-project-" + COUNTER.incrementAndGet();

        adminPost("/api/v1/projects", """
                {"key": "%s", "name": "Eval"}""".formatted(projectKey));
        adminPost("/api/v1/projects/" + projectKey + "/environments", """
                {"key": "prod", "name": "Production"}""");
        adminPost("/api/v1/projects/" + projectKey + "/segments", """
                {"key": "beta-testers", "name": "Beta testers",
                 "clauses": [{"attribute": "email", "operator": "ends_with", "values": ["@acme.test"]}]}""");
        adminPost("/api/v1/projects/" + projectKey + "/flags", """
                {"key": "new-checkout", "name": "New checkout"}""");
        assertThat(mvc.put().uri("/api/v1/projects/{p}/flags/new-checkout/config/prod", projectKey)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version": 0, "enabled": true, "defaultVariation": false, "offVariation": false,
                         "rules": [{"clauses": [{"operator": "in_segment", "values": ["beta-testers"]}],
                                    "rollout": {"type": "FIXED", "variation": true}}]}"""))
                .hasStatusOk();

        var keyResult = mvc.post().uri("/api/v1/projects/{p}/environments/prod/api-keys", projectKey)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"kind": "SERVER_SDK", "name": "prod sdk"}""")
                .exchange();
        assertThat(keyResult).hasStatus(HttpStatus.CREATED);
        sdkToken = readField(keyResult.getResponse(), "token");
    }

    private String loginAsAdmin() {
        var result = mvc.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "admin", "password": "admin-test-password"}""")
                .exchange();
        assertThat(result).hasStatusOk();
        return readField(result.getResponse(), "token");
    }

    private String readField(jakarta.servlet.http.HttpServletResponse response, String field) {
        try {
            return objectMapper.readTree(
                    ((org.springframework.mock.web.MockHttpServletResponse) response).getContentAsString())
                    .get(field).asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void adminPost(String uri, String body) {
        assertThat(mvc.post().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void segmentMemberGetsTrueWithRuleMatchReason() {
        assertThat(mvc.post().uri("/api/v1/evaluate/new-checkout")
                .header(HttpHeaders.AUTHORIZATION, sdkToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "user-1", "attributes": {"email": "dev@acme.test"}}"""))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.value", v -> v.assertThat().isEqualTo(true))
                .hasPathSatisfying("$.reason", r -> r.assertThat().isEqualTo("RULE_MATCH"))
                .hasPathSatisfying("$.matchedRuleId", id -> id.assertThat().asString().isNotBlank());
    }

    @Test
    void nonMemberFallsThrough() {
        assertThat(mvc.post().uri("/api/v1/evaluate/new-checkout")
                .header(HttpHeaders.AUTHORIZATION, sdkToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "user-2", "attributes": {"email": "visitor@example.test"}}"""))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.value", v -> v.assertThat().isEqualTo(false))
                .hasPathSatisfying("$.reason", r -> r.assertThat().isEqualTo("FALLTHROUGH"));
    }

    @Test
    void unknownFlagServesCallerDefault() {
        assertThat(mvc.post().uri("/api/v1/evaluate/no-such-flag")
                .header(HttpHeaders.AUTHORIZATION, sdkToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "user-1", "defaultValue": true}"""))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.value", v -> v.assertThat().isEqualTo(true))
                .hasPathSatisfying("$.reason", r -> r.assertThat().isEqualTo("FLAG_NOT_FOUND"));
    }

    @Test
    void archivedFlagServesCallerDefault() {
        assertThat(mvc.put().uri("/api/v1/projects/{p}/flags/new-checkout", projectKey)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "New checkout", "archived": true}"""))
                .hasStatusOk();

        assertThat(mvc.post().uri("/api/v1/evaluate/new-checkout")
                .header(HttpHeaders.AUTHORIZATION, sdkToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "user-1", "attributes": {"email": "dev@acme.test"}}"""))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.reason", r -> r.assertThat().isEqualTo("FLAG_NOT_FOUND"));
    }

    @Test
    void masterSwitchOffServesOffVariation() {
        assertThat(mvc.put().uri("/api/v1/projects/{p}/flags/new-checkout/config/prod", projectKey)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version": 1, "enabled": false, "defaultVariation": false, "offVariation": false, "rules": []}"""))
                .hasStatusOk();

        assertThat(mvc.post().uri("/api/v1/evaluate/new-checkout")
                .header(HttpHeaders.AUTHORIZATION, sdkToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "user-1", "attributes": {"email": "dev@acme.test"}}"""))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.value", v -> v.assertThat().isEqualTo(false))
                .hasPathSatisfying("$.reason", r -> r.assertThat().isEqualTo("OFF"));
    }

    @Test
    void adminMutationInvalidatesTheSnapshotCache() {
        // Prime the cache
        assertThat(mvc.post().uri("/api/v1/evaluate/new-checkout")
                .header(HttpHeaders.AUTHORIZATION, sdkToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "user-3", "attributes": {"email": "visitor@example.test"}}"""))
                .hasStatusOk()
                .bodyJson().hasPathSatisfying("$.value", v -> v.assertThat().isEqualTo(false));

        // Change the fallthrough default via the admin API
        assertThat(mvc.put().uri("/api/v1/projects/{p}/flags/new-checkout/config/prod", projectKey)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version": 1, "enabled": true, "defaultVariation": true, "offVariation": false, "rules": []}"""))
                .hasStatusOk();

        // The very next evaluation must see the new config
        assertThat(mvc.post().uri("/api/v1/evaluate/new-checkout")
                .header(HttpHeaders.AUTHORIZATION, sdkToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "user-3", "attributes": {"email": "visitor@example.test"}}"""))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.value", v -> v.assertThat().isEqualTo(true))
                .hasPathSatisfying("$.reason", r -> r.assertThat().isEqualTo("FALLTHROUGH"));
    }

    @Test
    void bulkEvaluatesEveryFlagForTheContext() {
        adminPost("/api/v1/projects/" + projectKey + "/flags", """
                {"key": "second-flag", "name": "Second flag"}""");

        assertThat(mvc.post().uri("/api/v1/evaluate")
                .header(HttpHeaders.AUTHORIZATION, sdkToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "user-1", "attributes": {"email": "dev@acme.test"}}"""))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.flags.new-checkout.value", v -> v.assertThat().isEqualTo(true))
                .hasPathSatisfying("$.flags.new-checkout.reason", r -> r.assertThat().isEqualTo("RULE_MATCH"))
                .hasPathSatisfying("$.flags.second-flag.reason", r -> r.assertThat().isEqualTo("OFF"));
    }

    @Test
    void jwtCannotReachTheEvaluationSurface() {
        assertThat(mvc.post().uri("/api/v1/evaluate/new-checkout")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "user-1"}"""))
                .hasStatus(HttpStatus.FORBIDDEN);
    }
}
