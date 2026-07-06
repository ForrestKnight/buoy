# Evaluation load test

Verifies the performance contract: evaluations are served from the in-process snapshot
cache with no database access, targeting **p99 < 5ms** locally and **zero 5xx** under load.

## Method

- 20 virtual users hammer `POST /api/v1/evaluate/{flagKey}`, 5 VUs hammer the bulk endpoint,
  for 30s each — after a one-request warm-up implied by the first iterations (the first
  evaluation per environment loads the snapshot; everything after is memory-only).
- User keys cycle through 10k identities so bucketing paths (murmur3 + weight mapping) are
  exercised, not a single cached branch.
- Thresholds fail the run if p99 ≥ 5ms on either scenario or any request fails.
- Numbers are only meaningful **local-to-local** (app and k6 on the same machine or LAN):
  the target bounds service time, not internet round-trips.

## Run it

```sh
# 1. Start the stack with demo data and grab the printed SDK key
docker compose up --build
# 2. Hammer it
k6 run -e BUOY_SDK_KEY=buoy_srv_... perf/evaluate.js
# (no local k6? docker run --rm -i --network host -e BUOY_SDK_KEY=... \
#    grafana/k6 run - < perf/evaluate.js)
```

The companion proof lives in the test suite: `EvaluationHotPathTest` asserts that a warmed
evaluation path executes **zero SQL statements**, so latency here is cache + JSON + HTTP,
never the database.
