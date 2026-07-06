# Buoy

Self-hosted feature flags for the JVM. Define flags with targeting rules in a small admin API,
ask a fast evaluation API "is this on for this user?", and change behavior in production without
a redeploy. One Spring Boot jar plus Postgres — `docker compose up` and you're running.

The open-source flag space has strong entries in Node (Unleash), Python (Flagsmith), and Go
(Flipt); on the JVM the options are embedded libraries, not a service you can stand up next to
your Spring apps. Buoy is that service: boring, auditable, and small enough to actually read.

## Features

- **Boolean flags** with ordered targeting rules: attribute clauses (`equals`, `in`, `contains`,
  `starts_with`, numeric and semver comparisons, ...) ANDed per rule, first match wins
- **Deterministic percentage rollouts** — murmur3-bucketed, sticky per user per flag, minimal
  reallocation when weights change; never random
- **Segments** — reusable clause sets (`beta-testers`, `enterprise-plan`) referenced from rules
- **Evaluation reasons** — every result says *why* (`RULE_MATCH` + rule id, `FALLTHROUGH`, `OFF`,
  `FLAG_NOT_FOUND`), which turns "why is this flag off for me" from a debugging session into a log line
- **Environments** — per-project (dev/staging/prod or whatever you like); flag definitions are
  shared, configurations are per-environment, SDK keys are per-environment
- **Hot path served from memory** — evaluations read an in-process snapshot cache, invalidated
  transactionally on change; the database is never on the request path
- **RBAC** — per-project OWNER / EDITOR / VIEWER, JWT console users, plus environment-scoped
  API keys (`buoy_srv_` for SDKs, `buoy_adm_` for automation), stored hashed
- **Audit log** — every mutation records who, what, and a before/after JSON diff

## Quickstart

```sh
git clone https://github.com/ForrestKnight/buoy && cd buoy
docker compose up --build
```

The bundled compose file starts Postgres and Buoy with the `demo` profile, which seeds two fake
projects and prints demo credentials and a ready-made SDK key to the log. Evaluate a flag:

```sh
# the demo SDK key is printed in the container log
curl -s -X POST localhost:8080/api/v1/evaluate/new-payment-flow \
  -H "Authorization: $BUOY_SDK_KEY" -H 'Content-Type: application/json' \
  -d '{"key": "user-42", "attributes": {"email": "dev@acme.test"}}'
```

```json
{"flagKey":"new-payment-flow","value":true,"reason":"RULE_MATCH","matchedRuleId":"..."}
```

Evaluate everything for a user in one call:

```sh
curl -s -X POST localhost:8080/api/v1/evaluate \
  -H "Authorization: $BUOY_SDK_KEY" -H 'Content-Type: application/json' \
  -d '{"key": "user-42", "attributes": {"plan": "enterprise", "country": "de"}}'
```

## Admin API in five curls

```sh
# 1. Log in (demo users: demo-owner / demo-editor / demo-viewer, password buoy-demo)
TOKEN=$(curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"username": "demo-owner", "password": "buoy-demo"}' | jq -r .token)
AUTH="Authorization: Bearer $TOKEN"

# 2. Create a flag (a disabled config appears in every environment automatically)
curl -s -X POST localhost:8080/api/v1/projects/acme-checkout/flags -H "$AUTH" \
  -H 'Content-Type: application/json' \
  -d '{"key": "my-new-flag", "name": "My new flag", "tags": ["demo"]}'

# 3. Enable it in production for beta testers, 25% rollout for everyone else
curl -s -X PUT localhost:8080/api/v1/projects/acme-checkout/flags/my-new-flag/config/production \
  -H "$AUTH" -H 'Content-Type: application/json' -d '{
    "version": 0, "enabled": true, "defaultVariation": false, "offVariation": false,
    "rules": [
      {"clauses": [{"operator": "in_segment", "values": ["beta-testers"]}],
       "rollout": {"type": "FIXED", "variation": true}},
      {"clauses": [{"attribute": "key", "operator": "starts_with", "values": ["user-"]}],
       "rollout": {"type": "PERCENTAGE", "weights": [
         {"variation": true,  "weightThousandths": 25000},
         {"variation": false, "weightThousandths": 75000}]}}
    ]}'

# 4. Concurrent-edit safety: replaying the same version is a 409, not a lost update
#    (re-fetch the config, use its current "version", retry)

# 5. Who changed what?
curl -s "localhost:8080/api/v1/projects/acme-checkout/audit?entityType=FLAG_CONFIG" -H "$AUTH"
```

Interactive API docs are served at `/swagger-ui.html`; the OpenAPI document is committed at
[`docs/openapi.json`](docs/openapi.json).

## Architecture

```
        SDK / your services                     engineers / CI
                │                                     │
     POST /api/v1/evaluate/*                 /auth/login → JWT
       (buoy_srv_… API key)              /api/v1/** (JWT or buoy_adm_…)
                │                                     │
┌───────────────▼─────────────────────────────────────▼───────────────┐
│  Spring Boot (one jar)                                              │
│                                                                     │
│  EvaluationService ──reads── EnvironmentSnapshotCache (Caffeine)    │
│        │                          ▲ invalidate (after commit)       │
│    Evaluator (pure)               │                                 │
│                          domain events ◄── admin services ── RBAC  │
│                                                    │                │
│                                            audit log (every write)  │
└────────────────────────────────────────────────────┬────────────────┘
                                                     │ JPA / Flyway
                                                 PostgreSQL 16
```

Evaluation semantics, in order: master switch off → `OFF`; first rule whose clauses all match
wins (`RULE_MATCH`); no match → `FALLTHROUGH`. Percentage rollouts bucket on
`murmur3_128(flagKey + ":" + userKey) % 100_000`, so a user's variation is stable across
requests, nodes, and restarts, and changing weights moves as few users as possible.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `BUOY_DB_HOST` / `BUOY_DB_PORT` / `BUOY_DB_NAME` / `BUOY_DB_USER` / `BUOY_DB_PASSWORD` | `localhost` / `5432` / `buoy` / `buoy` / `buoy` | Postgres connection |
| `BUOY_BOOTSTRAP_USERNAME` | `admin` | First-admin username (created only when no users exist) |
| `BUOY_BOOTSTRAP_PASSWORD` | *generated* | First-admin password; if unset, a one-time password is printed to the log |
| `BUOY_SECURITY_JWT_SECRET` | *generated* | HMAC secret (≥ 32 bytes) for admin tokens; set it so logins survive restarts |
| `BUOY_SECURITY_JWT_TTL` | `12h` | Admin token lifetime |

## Development

```sh
./gradlew build          # compiles + full test suite (needs Docker for Testcontainers)
./gradlew bootRun        # against a local Postgres (see variables above)
```

Java 25 via Gradle toolchain — the build provisions the JDK it needs. Schema changes are Flyway
migrations under `src/main/resources/db/migration`, append-only.

## Roadmap

Live flag updates over SSE (`GET /api/v1/stream`), multivariate flags, an OpenFeature provider,
a Java client SDK, webhooks, and more — tracked as [issues](../../issues).

## License

[Apache-2.0](LICENSE)
