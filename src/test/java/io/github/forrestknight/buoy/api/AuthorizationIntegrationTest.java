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
 * Authentication and per-project RBAC: JWT users with roles, instance-admin
 * bypass, and both kinds of API key.
 */
@SpringBootTest(properties = {
        "buoy.bootstrap.username=admin",
        "buoy.bootstrap.password=admin-test-password",
        "buoy.security.jwt-secret=integration-test-secret-0123456789abcdef"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthorizationIntegrationTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminBearer;
    private String suffix;

    @BeforeEach
    void setUp() {
        adminBearer = "Bearer " + login("admin", "admin-test-password");
        suffix = String.valueOf(COUNTER.incrementAndGet());
    }

    private String login(String username, String password) {
        var result = mvc.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "password": "%s"}""".formatted(username, password))
                .exchange();
        assertThat(result).hasStatusOk();
        return json(result.getResponse(), "token");
    }

    private String json(jakarta.servlet.http.HttpServletResponse response, String field) {
        try {
            return objectMapper.readTree(
                    ((org.springframework.mock.web.MockHttpServletResponse) response).getContentAsString())
                    .get(field).asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void createUser(String username, String password) {
        assertThat(mvc.post().uri("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, adminBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "password": "%s"}""".formatted(username, password)))
                .hasStatus(HttpStatus.CREATED);
    }

    private void assignRole(String projectKey, String username, String role) {
        assertThat(mvc.put().uri("/api/v1/projects/{p}/members/{u}", projectKey, username)
                .header(HttpHeaders.AUTHORIZATION, adminBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"role": "%s"}""".formatted(role)))
                .hasStatusOk();
    }

    private String projectWithFlag() {
        String projectKey = "authz-project-" + suffix;
        assertThat(mvc.post().uri("/api/v1/projects")
                .header(HttpHeaders.AUTHORIZATION, adminBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "%s", "name": "AuthZ"}""".formatted(projectKey)))
                .hasStatus(HttpStatus.CREATED);
        assertThat(mvc.post().uri("/api/v1/projects/{p}/environments", projectKey)
                .header(HttpHeaders.AUTHORIZATION, adminBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "prod", "name": "Production"}"""))
                .hasStatus(HttpStatus.CREATED);
        assertThat(mvc.post().uri("/api/v1/projects/{p}/flags", projectKey)
                .header(HttpHeaders.AUTHORIZATION, adminBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "some-flag", "name": "Some flag"}"""))
                .hasStatus(HttpStatus.CREATED);
        return projectKey;
    }

    private String issueApiKey(String projectKey, String kind) {
        var result = mvc.post().uri("/api/v1/projects/{p}/environments/prod/api-keys", projectKey)
                .header(HttpHeaders.AUTHORIZATION, adminBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"kind": "%s", "name": "test %s key"}""".formatted(kind, kind))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return json(result.getResponse(), "token");
    }

    @Test
    void wrongPasswordIsUnauthorizedProblem() {
        assertThat(mvc.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "admin", "password": "nope"}"""))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.title").isEqualTo("Authentication failed");
    }

    @Test
    void missingTokenIsUnauthorized() {
        assertThat(mvc.get().uri("/api/v1/projects")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void viewerCanReadButNotWrite() {
        String projectKey = projectWithFlag();
        String viewer = "viewer-" + suffix;
        createUser(viewer, "password-123");
        assignRole(projectKey, viewer, "VIEWER");
        String viewerBearer = "Bearer " + login(viewer, "password-123");

        assertThat(mvc.get().uri("/api/v1/projects/{p}/flags", projectKey)
                .header(HttpHeaders.AUTHORIZATION, viewerBearer))
                .hasStatusOk();

        assertThat(mvc.put().uri("/api/v1/projects/{p}/flags/some-flag", projectKey)
                .header(HttpHeaders.AUTHORIZATION, viewerBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Renamed", "archived": false}"""))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void editorCanEditFlagsButNotManageEnvironmentsOrKeys() {
        String projectKey = projectWithFlag();
        String editor = "editor-" + suffix;
        createUser(editor, "password-123");
        assignRole(projectKey, editor, "EDITOR");
        String editorBearer = "Bearer " + login(editor, "password-123");

        assertThat(mvc.put().uri("/api/v1/projects/{p}/flags/some-flag", projectKey)
                .header(HttpHeaders.AUTHORIZATION, editorBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Renamed by editor", "archived": false}"""))
                .hasStatusOk();

        assertThat(mvc.post().uri("/api/v1/projects/{p}/environments", projectKey)
                .header(HttpHeaders.AUTHORIZATION, editorBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "staging", "name": "Staging"}"""))
                .hasStatus(HttpStatus.FORBIDDEN);

        assertThat(mvc.post().uri("/api/v1/projects/{p}/environments/prod/api-keys", projectKey)
                .header(HttpHeaders.AUTHORIZATION, editorBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"kind": "SERVER_SDK", "name": "sneaky"}"""))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void nonMemberCannotReadProject() {
        String projectKey = projectWithFlag();
        String outsider = "outsider-" + suffix;
        createUser(outsider, "password-123");
        String outsiderBearer = "Bearer " + login(outsider, "password-123");

        assertThat(mvc.get().uri("/api/v1/projects/{p}", projectKey)
                .header(HttpHeaders.AUTHORIZATION, outsiderBearer))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void projectCreatorBecomesOwner() {
        String creator = "creator-" + suffix;
        createUser(creator, "password-123");
        String creatorBearer = "Bearer " + login(creator, "password-123");
        String projectKey = "owned-project-" + suffix;

        assertThat(mvc.post().uri("/api/v1/projects")
                .header(HttpHeaders.AUTHORIZATION, creatorBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "%s", "name": "Mine"}""".formatted(projectKey)))
                .hasStatus(HttpStatus.CREATED);

        // Owner-only action succeeds for the creator
        assertThat(mvc.post().uri("/api/v1/projects/{p}/environments", projectKey)
                .header(HttpHeaders.AUTHORIZATION, creatorBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "prod", "name": "Production"}"""))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void sdkKeyReachesEvaluationSurfaceOnly() {
        String projectKey = projectWithFlag();
        String sdkToken = issueApiKey(projectKey, "SERVER_SDK");

        // Admin surface: forbidden for SDK keys
        assertThat(mvc.get().uri("/api/v1/projects")
                .header(HttpHeaders.AUTHORIZATION, sdkToken))
                .hasStatus(HttpStatus.FORBIDDEN);

        // Evaluation surface: full access
        assertThat(mvc.post().uri("/api/v1/evaluate/some-flag")
                .header(HttpHeaders.AUTHORIZATION, sdkToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "user-1"}"""))
                .hasStatusOk()
                .bodyJson().hasPathSatisfying("$.reason", r -> r.assertThat().isEqualTo("OFF"));
    }

    @Test
    void adminKeyManagesItsOwnProjectOnly() {
        String projectKey = projectWithFlag();
        String adminToken = issueApiKey(projectKey, "ADMIN");

        assertThat(mvc.get().uri("/api/v1/projects/{p}/flags/some-flag", projectKey)
                .header(HttpHeaders.AUTHORIZATION, adminToken))
                .hasStatusOk();

        assertThat(mvc.put().uri("/api/v1/projects/{p}/flags/some-flag", projectKey)
                .header(HttpHeaders.AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Renamed by automation", "archived": false}"""))
                .hasStatusOk();

        // Owner-only surface is off limits for admin keys
        assertThat(mvc.delete().uri("/api/v1/projects/{p}", projectKey)
                .header(HttpHeaders.AUTHORIZATION, adminToken))
                .hasStatus(HttpStatus.FORBIDDEN);

        // Another project is invisible to it
        String otherKey = "other-project-" + suffix;
        assertThat(mvc.post().uri("/api/v1/projects")
                .header(HttpHeaders.AUTHORIZATION, adminBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "%s", "name": "Other"}""".formatted(otherKey)))
                .hasStatus(HttpStatus.CREATED);
        assertThat(mvc.get().uri("/api/v1/projects/{p}", otherKey)
                .header(HttpHeaders.AUTHORIZATION, adminToken))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void revokedKeyIsRejected() throws Exception {
        String projectKey = projectWithFlag();
        var created = mvc.post().uri("/api/v1/projects/{p}/environments/prod/api-keys", projectKey)
                .header(HttpHeaders.AUTHORIZATION, adminBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"kind": "ADMIN", "name": "short-lived"}""")
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        String token = json(created.getResponse(), "token");
        String keyId = objectMapper.readTree(
                ((org.springframework.mock.web.MockHttpServletResponse) created.getResponse())
                        .getContentAsString()).get("id").asText();

        assertThat(mvc.get().uri("/api/v1/projects/{p}", projectKey)
                .header(HttpHeaders.AUTHORIZATION, token))
                .hasStatusOk();

        assertThat(mvc.delete().uri("/api/v1/projects/{p}/environments/prod/api-keys/{id}", projectKey, keyId)
                .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(mvc.get().uri("/api/v1/projects/{p}", projectKey)
                .header(HttpHeaders.AUTHORIZATION, token))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
