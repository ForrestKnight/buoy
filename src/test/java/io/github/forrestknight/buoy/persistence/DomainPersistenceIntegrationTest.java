package io.github.forrestknight.buoy.persistence;

import io.github.forrestknight.buoy.TestcontainersConfiguration;
import io.github.forrestknight.buoy.domain.ActorType;
import io.github.forrestknight.buoy.domain.ApiKey;
import io.github.forrestknight.buoy.domain.ApiKeyKind;
import io.github.forrestknight.buoy.domain.AppUser;
import io.github.forrestknight.buoy.domain.AuditAction;
import io.github.forrestknight.buoy.domain.AuditLogEntry;
import io.github.forrestknight.buoy.domain.Clause;
import io.github.forrestknight.buoy.domain.Environment;
import io.github.forrestknight.buoy.domain.Flag;
import io.github.forrestknight.buoy.domain.FlagConfig;
import io.github.forrestknight.buoy.domain.Operator;
import io.github.forrestknight.buoy.domain.Project;
import io.github.forrestknight.buoy.domain.ProjectMember;
import io.github.forrestknight.buoy.domain.ProjectRole;
import io.github.forrestknight.buoy.domain.Rollout;
import io.github.forrestknight.buoy.domain.Segment;
import io.github.forrestknight.buoy.domain.TargetingRule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the V1 Flyway migration against real Postgres: Hibernate schema validation
 * passes, and the JSONB documents (rules, clauses, tags, diff) round-trip intact —
 * including Rollout's sealed-interface polymorphism.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class DomainPersistenceIntegrationTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private FlagConfigRepository flagConfigRepository;

    @Autowired
    private SegmentRepository segmentRepository;

    private Project project;
    private Environment environment;
    private Flag flag;

    private FlagConfig persistedConfig(List<TargetingRule> rules) {
        project = em.persist(new Project("acme-checkout", "Acme Checkout", null));
        environment = em.persist(new Environment(project, "production", "Production"));
        flag = em.persist(new Flag(project, "new-payment-flow", "New payment flow", null, List.of("payments")));
        FlagConfig config = new FlagConfig(flag, environment);
        config.setEnabled(true);
        config.setRules(rules);
        em.persistAndFlush(config);
        em.clear();
        return config;
    }

    @Test
    void targetingRulesRoundTripThroughJsonb() {
        List<TargetingRule> rules = List.of(
                new TargetingRule("rule-1",
                        List.of(new Clause("plan", Operator.IN, List.of("enterprise", "pro")),
                                new Clause("appVersion", Operator.SEMVER_GTE, List.of("2.1.0"))),
                        new Rollout.Fixed(true)),
                new TargetingRule("rule-2",
                        List.of(new Clause(null, Operator.IN_SEGMENT, List.of("beta-testers"))),
                        new Rollout.Percentage(List.of(
                                new Rollout.WeightedVariation(true, 25_000),
                                new Rollout.WeightedVariation(false, 75_000)))));

        FlagConfig saved = persistedConfig(rules);

        FlagConfig reloaded = flagConfigRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getRules()).isEqualTo(rules);
        assertThat(reloaded.getRules().get(1).rollout()).isInstanceOf(Rollout.Percentage.class);
        assertThat(reloaded.isEnabled()).isTrue();
        assertThat(reloaded.getDefaultVariation()).isTrue();
        assertThat(reloaded.getOffVariation()).isFalse();
    }

    @Test
    void optimisticLockVersionIncrementsOnUpdate() {
        FlagConfig saved = persistedConfig(List.of());
        FlagConfig reloaded = flagConfigRepository.findById(saved.getId()).orElseThrow();
        long initialVersion = reloaded.getVersion();

        reloaded.setEnabled(false);
        em.flush();

        assertThat(reloaded.getVersion()).isEqualTo(initialVersion + 1);
    }

    @Test
    void segmentClausesRoundTripThroughJsonb() {
        Project p = em.persist(new Project("acme-mobile", "Acme Mobile", null));
        List<Clause> clauses = List.of(
                new Clause("email", Operator.ENDS_WITH, List.of("@acme.test")),
                new Clause("country", Operator.NOT_IN, List.of("DE", "FR")));
        Segment segment = em.persistAndFlush(new Segment(p, "beta-testers", "Beta testers", "Internal beta cohort", clauses));
        em.clear();

        Segment reloaded = segmentRepository.findById(segment.getId()).orElseThrow();
        assertThat(reloaded.getClauses()).isEqualTo(clauses);
    }

    @Test
    void remainingEntitiesPersistAgainstMigratedSchema() {
        Project p = em.persist(new Project("acme-api", "Acme API", null));
        Environment env = em.persist(new Environment(p, "staging", "Staging"));
        AppUser user = em.persist(new AppUser("forrest", "$2a$10$bcrytPlaceholderHashValue.1234567890123456789012345", "Forrest", true));
        em.persist(new ProjectMember(p, user, ProjectRole.OWNER));
        em.persist(new ApiKey(env, ApiKeyKind.SERVER_SDK, "staging sdk key", "a".repeat(64), "buoy_srv_a1b2"));
        AuditLogEntry entry = em.persistAndFlush(new AuditLogEntry(
                ActorType.USER, "forrest", AuditAction.UPDATED, "FLAG_CONFIG",
                42L, "new-payment-flow", p.getId(), env.getId(),
                "{\"before\":{\"enabled\":false},\"after\":{\"enabled\":true}}", "admin-api"));
        em.clear();

        AuditLogEntry reloaded = em.find(AuditLogEntry.class, entry.getId());
        assertThat(reloaded.getDiff()).contains("\"after\"");
        assertThat(reloaded.getOccurredAt()).isNotNull();
    }
}
