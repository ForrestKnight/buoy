package io.github.forrestknight.buoy.config;

import io.github.forrestknight.buoy.domain.ApiKeyKind;
import io.github.forrestknight.buoy.domain.Clause;
import io.github.forrestknight.buoy.domain.Operator;
import io.github.forrestknight.buoy.domain.ProjectRole;
import io.github.forrestknight.buoy.domain.Rollout;
import io.github.forrestknight.buoy.domain.TargetingRule;
import io.github.forrestknight.buoy.persistence.ProjectRepository;
import io.github.forrestknight.buoy.service.ApiKeyService;
import io.github.forrestknight.buoy.service.EnvironmentService;
import io.github.forrestknight.buoy.service.FlagService;
import io.github.forrestknight.buoy.service.ProjectMemberService;
import io.github.forrestknight.buoy.service.ProjectService;
import io.github.forrestknight.buoy.service.SegmentService;
import io.github.forrestknight.buoy.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds a realistic, obviously-fake dataset for demos and local exploration:
 * two Acme projects, five environments, ~15 flags with rules, segments,
 * synthetic users, and a ready-to-use SDK key printed to the log. Runs only
 * under the {@code demo} profile and only into an empty instance. Everything
 * goes through the real services, so the audit log fills up like real traffic.
 */
@Component
@Profile("demo")
@Order(2)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String DEMO_PASSWORD = "buoy-demo";

    private final ProjectRepository projectRepository;
    private final ProjectService projects;
    private final EnvironmentService environments;
    private final FlagService flags;
    private final SegmentService segments;
    private final UserService users;
    private final ProjectMemberService members;
    private final ApiKeyService apiKeys;

    public DemoDataSeeder(ProjectRepository projectRepository, ProjectService projects,
                          EnvironmentService environments, FlagService flags, SegmentService segments,
                          UserService users, ProjectMemberService members, ApiKeyService apiKeys) {
        this.projectRepository = projectRepository;
        this.projects = projects;
        this.environments = environments;
        this.flags = flags;
        this.segments = segments;
        this.users = users;
        this.members = members;
        this.apiKeys = apiKeys;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (projectRepository.existsByKey("acme-checkout")) {
            log.info("Demo data already present; skipping seed");
            return;
        }
        seedUsers();
        seedAcmeCheckout();
        seedAcmeMobile();
        String sdkToken = apiKeys.create("acme-checkout", "production",
                ApiKeyKind.SERVER_SDK, "demo production sdk").token();
        log.info("""

                *************************************************************
                Buoy demo data seeded.

                Console users (password for all: {}):
                  demo-owner   (owner of both projects)
                  demo-editor  (editor on acme-checkout)
                  demo-viewer  (viewer on acme-checkout)

                SDK key for acme-checkout/production:
                  {}

                Try it:
                  curl -s -X POST localhost:8080/api/v1/evaluate/new-payment-flow \\
                    -H 'Authorization: {}' -H 'Content-Type: application/json' \\
                    -d '{"key":"user-42","attributes":{"email":"dev@acme.test"}}'
                *************************************************************
                """, DEMO_PASSWORD, sdkToken, sdkToken);
    }

    private void seedUsers() {
        users.create("demo-owner", DEMO_PASSWORD, "Demo Owner", false);
        users.create("demo-editor", DEMO_PASSWORD, "Demo Editor", false);
        users.create("demo-viewer", DEMO_PASSWORD, "Demo Viewer", false);
    }

    private void seedAcmeCheckout() {
        projects.create("acme-checkout", "Acme Checkout",
                "Web checkout service for the fictional Acme storefront", "demo-owner");
        environments.create("acme-checkout", "development", "Development");
        environments.create("acme-checkout", "staging", "Staging");
        environments.create("acme-checkout", "production", "Production");
        members.assign("acme-checkout", "demo-editor", ProjectRole.EDITOR);
        members.assign("acme-checkout", "demo-viewer", ProjectRole.VIEWER);

        segments.create("acme-checkout", "beta-testers", "Beta testers",
                "Internal beta cohort",
                List.of(clause("email", Operator.ENDS_WITH, "@acme.test")));
        segments.create("acme-checkout", "enterprise-plan", "Enterprise plan",
                "Customers on the enterprise plan",
                List.of(clause("plan", Operator.EQUALS, "enterprise")));
        segments.create("acme-checkout", "eu-users", "EU users",
                "Users in EU countries we ship to",
                List.of(new Clause("country", Operator.IN, List.of("de", "fr", "nl", "es", "it"))));

        flag("acme-checkout", "new-payment-flow", "New payment flow",
                "Stripe Payment Element instead of the legacy card form", List.of("payments"));
        config("acme-checkout", "new-payment-flow", "production", true, false, false,
                rule(segmentMatch("beta-testers"), fixed(true)));

        flag("acme-checkout", "checkout-redesign", "Checkout redesign",
                "2026 single-page checkout", List.of("ui"));
        config("acme-checkout", "checkout-redesign", "production", true, false, false,
                rule(clause("key", Operator.STARTS_WITH, "user-"), percentage(25_000)));

        flag("acme-checkout", "express-shipping", "Express shipping",
                "Same-day shipping option at checkout", List.of("logistics"));
        config("acme-checkout", "express-shipping", "production", true, true, false);

        flag("acme-checkout", "dark-mode", "Dark mode",
                "Dark theme for the checkout pages", List.of("ui"));
        config("acme-checkout", "dark-mode", "staging", true, false, false,
                rule(clause("key", Operator.STARTS_WITH, "user-"), percentage(50_000)));

        flag("acme-checkout", "fraud-check-v2", "Fraud check v2",
                "New ML fraud scoring before payment capture", List.of("payments", "risk"));
        config("acme-checkout", "fraud-check-v2", "production", true, false, false,
                rule(segmentMatch("enterprise-plan"), fixed(true)),
                rule(clause("key", Operator.STARTS_WITH, "user-"), percentage(10_000)));

        flag("acme-checkout", "gdpr-consent-banner", "GDPR consent banner",
                "Cookie consent banner for EU traffic", List.of("compliance"));
        config("acme-checkout", "gdpr-consent-banner", "production", true, false, false,
                rule(segmentMatch("eu-users"), fixed(true)));

        flag("acme-checkout", "min-app-version-gate", "Minimum app version gate",
                "Require app 2.1.0+ for the new checkout API", List.of("mobile"));
        config("acme-checkout", "min-app-version-gate", "production", true, false, false,
                rule(clause("appVersion", Operator.SEMVER_GTE, "2.1.0"), fixed(true)));

        flag("acme-checkout", "free-trial-extension", "Free trial extension",
                "Extend trial from 14 to 30 days for a test cohort", List.of("growth"));
        config("acme-checkout", "free-trial-extension", "production", true, false, false,
                rule(clause("plan", Operator.EQUALS, "free"), percentage(10_000)));

        flag("acme-checkout", "legacy-cart-cleanup", "Legacy cart cleanup",
                "Fully rolled out in 2025; kept for the archive demo", List.of("cleanup"));
        flags.update("acme-checkout", "legacy-cart-cleanup", "Legacy cart cleanup",
                "Fully rolled out in 2025; kept for the archive demo", List.of("cleanup"), true);
    }

    private void seedAcmeMobile() {
        projects.create("acme-mobile", "Acme Mobile App",
                "iOS and Android storefront app", "demo-owner");
        environments.create("acme-mobile", "development", "Development");
        environments.create("acme-mobile", "production", "Production");

        segments.create("acme-mobile", "android-beta", "Android beta",
                "Android users on the 3.x beta track",
                List.of(clause("platform", Operator.EQUALS, "android"),
                        clause("appVersion", Operator.SEMVER_GTE, "3.0.0-beta")));

        flag("acme-mobile", "push-notifications-v2", "Push notifications v2",
                "New notification pipeline", List.of("messaging"));
        config("acme-mobile", "push-notifications-v2", "production", true, false, false,
                rule(clause("key", Operator.STARTS_WITH, "user-"), percentage(5_000)));

        flag("acme-mobile", "offline-mode", "Offline mode",
                "Browse cached catalog without a connection", List.of("mobile"));
        config("acme-mobile", "offline-mode", "development", true, true, false);

        flag("acme-mobile", "new-onboarding", "New onboarding",
                "Three-screen onboarding flow", List.of("growth", "ui"));
        config("acme-mobile", "new-onboarding", "production", true, false, false,
                rule(segmentMatch("android-beta"), fixed(true)));

        flag("acme-mobile", "biometric-login", "Biometric login",
                "FaceID / fingerprint login", List.of("auth"));
        config("acme-mobile", "biometric-login", "production", true, false, false,
                rule(new Clause("platform", Operator.IN, List.of("ios")), fixed(true)));

        flag("acme-mobile", "image-cdn-migration", "Image CDN migration",
                "Serve product images from the new CDN", List.of("infra"));
        config("acme-mobile", "image-cdn-migration", "production", true, true, false);

        flag("acme-mobile", "haptic-feedback", "Haptic feedback",
                "Vibration on add-to-cart", List.of("ui"));
    }

    private void flag(String projectKey, String key, String name, String description, List<String> tags) {
        flags.create(projectKey, key, name, description, tags);
    }

    private void config(String projectKey, String flagKey, String environmentKey,
                        boolean enabled, boolean defaultVariation, boolean offVariation,
                        TargetingRule... rules) {
        flags.updateConfig(projectKey, flagKey, environmentKey, 0, enabled,
                List.of(rules), defaultVariation, offVariation);
    }

    private static Clause clause(String attribute, Operator operator, String value) {
        return new Clause(attribute, operator, List.of(value));
    }

    private static Clause segmentMatch(String segmentKey) {
        return new Clause(null, Operator.IN_SEGMENT, List.of(segmentKey));
    }

    private static TargetingRule rule(Clause clause, Rollout rollout) {
        return new TargetingRule(null, List.of(clause), rollout);
    }

    private static Rollout fixed(boolean variation) {
        return new Rollout.Fixed(variation);
    }

    private static Rollout percentage(int trueWeightThousandths) {
        return new Rollout.Percentage(List.of(
                new Rollout.WeightedVariation(true, trueWeightThousandths),
                new Rollout.WeightedVariation(false, 100_000 - trueWeightThousandths)));
    }
}
