package io.github.forrestknight.buoy.api;

import io.github.forrestknight.buoy.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the generated OpenAPI document and refreshes the committed artifact
 * at {@code docs/openapi.json} — API drift shows up as a git diff.
 */
@SpringBootTest(properties = {
        "buoy.bootstrap.username=admin",
        "buoy.bootstrap.password=admin-test-password",
        "buoy.security.jwt-secret=integration-test-secret-0123456789abcdef"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiDocsTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void openApiDocumentCoversBothSurfacesAndIsCommitted() throws Exception {
        var result = mvc.get().uri("/v3/api-docs").exchange();
        assertThat(result).hasStatusOk();
        String body = ((org.springframework.mock.web.MockHttpServletResponse) result.getResponse())
                .getContentAsString(StandardCharsets.UTF_8);

        var document = objectMapper.readTree(body);
        assertThat(document.get("info").get("title").asText()).isEqualTo("Buoy API");
        assertThat(document.get("paths").properties().stream().map(java.util.Map.Entry::getKey))
                .contains("/api/v1/evaluate/{flagKey}", "/api/v1/evaluate",
                        "/auth/login", "/api/v1/projects",
                        "/api/v1/projects/{projectKey}/flags/{flagKey}/config/{environmentKey}",
                        "/api/v1/projects/{projectKey}/audit")
                // The SSE stream is roadmap, not implementation — never advertise a 404
                .doesNotContain("/api/v1/stream");

        Path artifact = Path.of("docs", "openapi.json");
        Files.createDirectories(artifact.getParent());
        Files.writeString(artifact, objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(document) + System.lineSeparator());
    }
}
