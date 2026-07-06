package io.github.forrestknight.buoy.api;

import io.github.forrestknight.buoy.TestcontainersConfiguration;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
 * Proves the performance contract instead of asserting it in prose: once the
 * snapshot and key-authentication caches are warm, evaluation requests execute
 * zero SQL statements (spec 0001). Counted via Hibernate statistics — every
 * database access in this application goes through Hibernate.
 */
@SpringBootTest(properties = {
        "buoy.bootstrap.username=admin",
        "buoy.bootstrap.password=admin-test-password",
        "buoy.security.jwt-secret=integration-test-secret-0123456789abcdef",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EvaluationHotPathTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void warmedEvaluationPathExecutesZeroSql() {
        String bearer = "Bearer " + login();
        adminPost(bearer, "/api/v1/projects", """
                {"key": "hotpath-project", "name": "Hot path"}""");
        adminPost(bearer, "/api/v1/projects/hotpath-project/environments", """
                {"key": "prod", "name": "Production"}""");
        adminPost(bearer, "/api/v1/projects/hotpath-project/flags", """
                {"key": "hot-flag", "name": "Hot flag"}""");
        assertThat(mvc.put().uri("/api/v1/projects/hotpath-project/flags/hot-flag/config/prod")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version": 0, "enabled": true, "defaultVariation": false, "offVariation": false,
                         "rules": [{"clauses": [{"attribute": "key", "operator": "starts_with", "values": ["user-"]}],
                                    "rollout": {"type": "PERCENTAGE", "weights": [
                                      {"variation": true, "weightThousandths": 50000},
                                      {"variation": false, "weightThousandths": 50000}]}}]}"""))
                .hasStatusOk();
        var keyResult = mvc.post().uri("/api/v1/projects/hotpath-project/environments/prod/api-keys")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"kind": "SERVER_SDK", "name": "hot sdk"}""")
                .exchange();
        assertThat(keyResult).hasStatus(HttpStatus.CREATED);
        String sdkToken = read(keyResult.getResponse(), "token");

        // Warm both caches: key authentication and the environment snapshot
        evaluateOk(sdkToken, "/api/v1/evaluate/hot-flag");
        evaluateOk(sdkToken, "/api/v1/evaluate");

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        for (int i = 0; i < 25; i++) {
            evaluateOk(sdkToken, "/api/v1/evaluate/hot-flag");
            evaluateOk(sdkToken, "/api/v1/evaluate");
        }

        assertThat(statistics.getPrepareStatementCount())
                .as("SQL statements executed by 50 evaluation requests on a warm cache")
                .isZero();
    }

    private void evaluateOk(String sdkToken, String uri) {
        assertThat(mvc.post().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, sdkToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key": "user-7", "attributes": {"plan": "pro"}}"""))
                .hasStatusOk();
    }

    private String login() {
        var result = mvc.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "admin", "password": "admin-test-password"}""")
                .exchange();
        assertThat(result).hasStatusOk();
        return read(result.getResponse(), "token");
    }

    private String read(jakarta.servlet.http.HttpServletResponse response, String field) {
        try {
            return objectMapper.readTree(
                    ((org.springframework.mock.web.MockHttpServletResponse) response).getContentAsString())
                    .get(field).asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void adminPost(String bearer, String uri, String body) {
        assertThat(mvc.post().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .hasStatus(HttpStatus.CREATED);
    }
}
