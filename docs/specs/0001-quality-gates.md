# 0001 — Quality gates: architecture boundaries, property tests, hot-path proof

## Problem

The codebase makes several claims that are currently enforced by review and discipline only:

1. **Layering** — `api` talks to services, never to `persistence`; `domain` (including the
   evaluation engine) stays framework-free so it is exhaustively testable and one day
   extractable into a client SDK.
2. **Bucketing correctness** — percentage rollouts are deterministic, sticky, statistically
   fair, and reallocate minimally on weight changes. Example-based tests pin known cases, but
   the properties should hold for *all* inputs, not the ones we thought of.
3. **The performance contract** — "evaluation never touches the database" is an architectural
   claim; nothing fails today if someone adds a repository call to the hot path.
4. **Error responses are a product feature** — problem-details bodies are asserted piecemeal;
   their exact shape should be pinned so accidental changes to the error contract are visible.
5. **API documentation drift** — `docs/openapi.json` is refreshed by a test locally, but CI
   does not fail when a committed artifact goes stale.

Claims without enforcement rot. Each becomes a build-failing gate.

## Approaches considered

- **ArchUnit vs. Gradle module split.** Splitting the engine into a separate Gradle module
  enforces boundaries at compile time, but fights the single-jar, no-ceremony layout. ArchUnit
  rules express the same constraints inside one module and fail the build with readable
  messages. Chosen: ArchUnit.
- **jqwik vs. hand-rolled randomized tests.** Hand-rolled loops over `Random` inputs don't
  shrink failures and hide their seed. jqwik generates, shrinks to minimal counterexamples,
  and reports seeds for reproduction. Chosen: jqwik, keeping the existing example/golden tests
  (they pin the exact hash mapping, which properties can't).
- **Zero-SQL proof: datasource proxy vs. Hibernate statistics.** A wrapping datasource proxy
  counts every JDBC statement but adds a test-only bean graph; Hibernate's `Statistics`
  (`generate_statistics` in the test profile) counts prepared statements with zero extra
  wiring, and all persistence here goes through Hibernate. Chosen: Hibernate statistics,
  asserting `getPrepareStatementCount()` is unchanged across evaluations after cache warm-up.
- **Snapshot style: committed golden files vs. inline expected JSON.** Golden files shine for
  large documents; problem-details bodies are ~10 lines and read best next to the assertion.
  Chosen: inline expected JSON compared as trees (field order irrelevant), dynamic values
  asserted separately.
- **Null-safety: JSpecify annotations vs. adding a checker (NullAway).** A checker is the end
  state but drags in Error Prone configuration; the immediate value is declaring the contract.
  Chosen: `@NullMarked` per package + `@Nullable` at the actual nullable seams now; a checker
  can be a follow-up.

## Design

- `src/test/java/.../ArchitectureTest.java` — three rules: `api` classes never depend on
  `persistence`; `domain` depends on no other project package and imports no Spring; the
  evaluation engine types (`Evaluator`, `Bucketing`, `SemVer`, evaluation records) additionally
  import no JPA/Jackson-databind — pure JDK + commons-codec.
- `src/test/java/.../BucketingPropertiesTest.java` (jqwik) — properties over arbitrary
  flag/context keys and generated weight tables: determinism, full-range bucket domain,
  distribution within tolerance, monotone minimal reallocation for two-way splits.
- `src/test/java/.../EvaluationHotPathTest.java` — full-stack IT: warm the cache with one
  evaluation, snapshot the Hibernate prepared-statement count, run N single + bulk
  evaluations, assert the count is unchanged.
- `src/test/java/.../ProblemDetailsSnapshotTest.java` — one test per error family (400
  bean-validation with `fieldErrors`, 400 semantic, 401 login, 404, 409 duplicate, 409 stale
  version), comparing the full JSON body as a tree.
- JSpecify: `org.jspecify:jspecify` dependency; `package-info.java` with `@NullMarked` for all
  five packages; `@Nullable` on nullable record components, entity accessors, and service
  parameters.
- `perf/`: k6 script hammering single + bulk evaluation with thresholds (`p(99)<5`ms local,
  zero 5xx), plus `perf/README.md` with methodology and how to reproduce.
- CI: single workflow — build (unit + ArchUnit + ITs via `./gradlew build`), OpenAPI drift
  gate (`git diff --exit-code docs/openapi.json` after the build), Docker image build.

## Test plan

The unit *is* tests; the meta-test is watching each gate catch a seeded violation locally
before trusting it: an api→persistence import (ArchUnit), a biased weight table (jqwik), a
repository call in `EvaluationService` (hot-path), a changed error title (snapshot), and an
uncommitted regeneration of `docs/openapi.json` (drift gate).
