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
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the exact problem-details bodies for every error family (spec 0001):
 * error responses are API contract, and contract changes must be deliberate.
 * Whole-body tree comparison — a new or renamed field fails here.
 */
@SpringBootTest(properties = {
        "buoy.bootstrap.username=admin",
        "buoy.bootstrap.password=admin-test-password",
        "buoy.security.jwt-secret=integration-test-secret-0123456789abcdef"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProblemDetailsSnapshotTest {

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

    private void assertBody(MvcTestResult result, HttpStatus expectedStatus, String expectedJson) {
        assertThat(result).hasStatus(expectedStatus);
        try {
            JsonNode actual = objectMapper.readTree(
                    ((org.springframework.mock.web.MockHttpServletResponse) result.getResponse())
                            .getContentAsString());
            JsonNode expected = objectMapper.readTree(expectedJson);
            assertThat(actual).isEqualTo(expected);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void notFound() {
        assertBody(mvc.get().uri("/api/v1/projects/ghost-project")
                        .header(HttpHeaders.AUTHORIZATION, bearer).exchange(),
                HttpStatus.NOT_FOUND, """
                        {"title": "Resource not found",
                         "status": 404,
                         "detail": "Project 'ghost-project' not found",
                         "instance": "/api/v1/projects/ghost-project"}""");
    }

    @Test
    void beanValidationWithFieldErrors() {
        assertBody(mvc.post().uri("/api/v1/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key": "Not_Kebab", "name": "Bad"}""").exchange(),
                HttpStatus.BAD_REQUEST, """
                        {"title": "Validation failed",
                         "status": 400,
                         "detail": "One or more request fields are invalid",
                         "instance": "/api/v1/projects",
                         "fieldErrors": {"key": "must be kebab-case: lowercase letters, digits, and single hyphens"}}""");
    }

    @Test
    void semanticValidation() {
        assertThat(mvc.post().uri("/api/v1/projects")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "snapshot-semantic", "name": "Semantic"}"""))
                .hasStatus(HttpStatus.CREATED);
        assertBody(mvc.post().uri("/api/v1/projects/snapshot-semantic/segments")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key": "nested", "name": "Nested",
                                 "clauses": [{"operator": "in_segment", "values": ["other"]}]}""").exchange(),
                HttpStatus.BAD_REQUEST, """
                        {"title": "Validation failed",
                         "status": 400,
                         "detail": "Segments cannot reference other segments",
                         "instance": "/api/v1/projects/snapshot-semantic/segments"}""");
    }

    @Test
    void invalidCredentials() {
        assertBody(mvc.post().uri("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "admin", "password": "wrong"}""").exchange(),
                HttpStatus.UNAUTHORIZED, """
                        {"title": "Authentication failed",
                         "status": 401,
                         "detail": "Invalid username or password",
                         "instance": "/auth/login"}""");
    }

    @Test
    void duplicateKey() {
        assertThat(mvc.post().uri("/api/v1/projects")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "snapshot-dupe", "name": "First"}"""))
                .hasStatus(HttpStatus.CREATED);
        assertBody(mvc.post().uri("/api/v1/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key": "snapshot-dupe", "name": "Second"}""").exchange(),
                HttpStatus.CONFLICT, """
                        {"title": "Duplicate key",
                         "status": 409,
                         "detail": "Project with key 'snapshot-dupe' already exists",
                         "instance": "/api/v1/projects"}""");
    }

    @Test
    void staleVersion() {
        assertThat(mvc.post().uri("/api/v1/projects")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "snapshot-stale", "name": "Stale"}"""))
                .hasStatus(HttpStatus.CREATED);
        assertThat(mvc.post().uri("/api/v1/projects/snapshot-stale/environments")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "prod", "name": "Production"}"""))
                .hasStatus(HttpStatus.CREATED);
        assertThat(mvc.post().uri("/api/v1/projects/snapshot-stale/flags")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "some-flag", "name": "Some flag"}"""))
                .hasStatus(HttpStatus.CREATED);

        assertBody(mvc.put().uri("/api/v1/projects/snapshot-stale/flags/some-flag/config/prod")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 5, "enabled": true, "defaultVariation": true,
                                 "offVariation": false, "rules": []}""").exchange(),
                HttpStatus.CONFLICT, """
                        {"title": "Version conflict",
                         "status": 409,
                         "detail": "Version conflict: request based on version 5 but current version is 0; re-fetch and retry",
                         "instance": "/api/v1/projects/snapshot-stale/flags/some-flag/config/prod"}""");
    }
}
