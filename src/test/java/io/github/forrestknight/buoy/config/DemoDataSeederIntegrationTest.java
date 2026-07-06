package io.github.forrestknight.buoy.config;

import io.github.forrestknight.buoy.TestcontainersConfiguration;
import io.github.forrestknight.buoy.persistence.FlagRepository;
import io.github.forrestknight.buoy.persistence.ProjectRepository;
import io.github.forrestknight.buoy.persistence.SegmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "buoy.bootstrap.username=admin",
        "buoy.bootstrap.password=admin-test-password",
        "buoy.security.jwt-secret=integration-test-secret-0123456789abcdef"
})
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
class DemoDataSeederIntegrationTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private FlagRepository flagRepository;

    @Autowired
    private SegmentRepository segmentRepository;

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private ApplicationArguments applicationArguments;

    @Test
    void seedsTwoProjectsWithFlagsAndSegments() {
        var checkout = projectRepository.findByKey("acme-checkout").orElseThrow();
        var mobile = projectRepository.findByKey("acme-mobile").orElseThrow();

        assertThat(flagRepository.findByProjectId(checkout.getId())).hasSize(9);
        assertThat(flagRepository.findByProjectId(mobile.getId())).hasSize(6);
        assertThat(segmentRepository.findByProjectId(checkout.getId())).hasSize(3);
        assertThat(segmentRepository.findByProjectId(mobile.getId())).hasSize(1);

        var archived = flagRepository.findByProjectIdAndKey(checkout.getId(), "legacy-cart-cleanup").orElseThrow();
        assertThat(archived.isArchived()).isTrue();
    }

    @Test
    void reseedingIsIdempotent() {
        long before = projectRepository.count();
        seeder.run(applicationArguments);
        assertThat(projectRepository.count()).isEqualTo(before);
    }
}
