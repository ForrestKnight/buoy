package io.github.forrestknight.buoy.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;

@Configuration
public class OpenApiConfig {

    static {
        // JsonNode fields (audit diffs) are free-form JSON, not a bean to introspect —
        // reflective introspection also makes the generated document non-deterministic
        // across JVMs, which the CI drift gate rightly refuses.
        SpringDocUtils.getConfig().replaceWithClass(JsonNode.class, Object.class);
    }

    @Bean
    OpenAPI buoyOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Buoy API")
                .description("""
                        Self-hosted feature flag service. Two surfaces: the evaluation API \
                        (SERVER_SDK key auth — the environment is implied by the key) and the \
                        admin API (JWT from /auth/login, or an ADMIN API key for automation). \
                        Live flag updates over SSE (GET /api/v1/stream) are on the roadmap and \
                        not part of this document.""")
                .license(new License().name("Apache-2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0"))
                .version("v1"));
    }
}
