// k6 load script for the evaluation hot path: single + bulk evaluation.
// See perf/README.md for methodology and how to run.
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BUOY_URL || 'http://localhost:8080';
const SDK_KEY = __ENV.BUOY_SDK_KEY; // required: a SERVER_SDK key
const FLAG_KEY = __ENV.BUOY_FLAG_KEY || 'new-payment-flow';

export const options = {
  scenarios: {
    single: {
      executor: 'constant-vus',
      exec: 'single',
      vus: 20,
      duration: '30s',
    },
    bulk: {
      executor: 'constant-vus',
      exec: 'bulk',
      vus: 5,
      duration: '30s',
    },
  },
  thresholds: {
    // The brief's target: p99 < 5ms locally, zero server errors.
    'http_req_duration{scenario:single}': ['p(99)<5'],
    'http_req_duration{scenario:bulk}': ['p(99)<5'],
    http_req_failed: ['rate==0'],
  },
};

const params = {
  headers: {
    Authorization: SDK_KEY,
    'Content-Type': 'application/json',
  },
};

function body(userId) {
  return JSON.stringify({
    key: `user-${userId}`,
    attributes: { email: `user-${userId}@acme.test`, plan: 'pro', country: 'de' },
  });
}

export function single() {
  const res = http.post(`${BASE_URL}/api/v1/evaluate/${FLAG_KEY}`, body(__ITER % 10000), params);
  check(res, { 'status 200': (r) => r.status === 200 });
}

export function bulk() {
  const res = http.post(`${BASE_URL}/api/v1/evaluate`, body(__ITER % 10000), params);
  check(res, { 'status 200': (r) => r.status === 200 });
}
