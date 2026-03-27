// REST API Throughput & Latency Test
// Ramps from 10 to 200 concurrent users to measure response times and rate limiting.


import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';

// Custom metrics
const notifLatency   = new Trend('notification_send_latency_ms', true);
const successCount   = new Counter('notifications_queued_ok');
const rateLimitedCnt = new Counter('requests_rate_limited');
const errorCount     = new Counter('server_errors');
const successRate    = new Rate('success_rate');

export const options = {
  stages: [
    { duration: '15s', target: 10  },
    { duration: '30s', target: 50  },
    { duration: '30s', target: 100 },
    { duration: '30s', target: 200 },
    { duration: '30s', target: 200 },
    { duration: '15s', target: 0   },
  ],

  thresholds: {
    // 95% of requests must complete within 500ms
    'notification_send_latency_ms': ['p(95)<500'],
    // At least 90% of all requests must succeed (200 or 429)
    'success_rate': ['rate>0.9'],
    // Zero 500 errors allowed
    'server_errors': ['count==0'],
  },
};

export default function () {
  // Each virtual user (VU) has its own API key → own rate limit bucket
  const apiKey = `api_key_vu_${__VU}`;
  const userId = `user_vu_${__VU}_iter_${__ITER}`;

  const payload = JSON.stringify({
    targetUserId: userId,
    title:        `Notification from VU ${__VU}`,
    body:         `Iteration ${__ITER} — testing throughput and latency`,
    channels:     ['WEBSOCKET'],
    retryCount:   0,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-API-KEY':    apiKey,
    },
    timeout: '5s',
  };

  const start = Date.now();
  const res   = http.post('http://localhost:8080/api/v1/notify', payload, params);
  const durationMs = Date.now() - start;

  // Record latency
  notifLatency.add(durationMs);

  // Categorise response
  if (res.status === 200) {
    successCount.add(1);
    successRate.add(true);
  } else if (res.status === 429) {
    rateLimitedCnt.add(1);
    successRate.add(true); // 429 is expected & valid — rate limiter is working
  } else {
    errorCount.add(1);
    successRate.add(false);
    console.error(`VU ${__VU} got unexpected status ${res.status}: ${res.body}`);
  }

  check(res, {
    'Status is 200 or 429':     (r) => r.status === 200 || r.status === 429,
    'No 500 server error':       (r) => r.status !== 500,
    'Response under 500ms':      ()  => durationMs < 500,
  });

  sleep(0.1); // 100ms think time between requests
}

export function handleSummary(data) {
  const latency = data.metrics['notification_send_latency_ms'];
  const success = data.metrics['notifications_queued_ok'];
  const limited = data.metrics['requests_rate_limited'];
  const errors  = data.metrics['server_errors'];

  console.log('\n========== TEST 1 SUMMARY: REST Throughput ==========');
  if (latency) {
    console.log(`Latency p50:  ${latency.values['p(50)'].toFixed(1)}ms`);
    console.log(`Latency p90:  ${latency.values['p(90)'].toFixed(1)}ms`);
    console.log(`Latency p95:  ${latency.values['p(95)'].toFixed(1)}ms`);
    console.log(`Latency p99:  ${latency.values['p(99)'].toFixed(1)}ms`);
    console.log(`Latency max:  ${latency.values['max'].toFixed(1)}ms`);
  }
  console.log(`Notifications queued OK: ${success ? success.values.count : 0}`);
  console.log(`Rate limited (429):      ${limited ? limited.values.count : 0}`);
  console.log(`Server errors:           ${errors  ? errors.values.count  : 0}`);
  console.log('=====================================================\n');
}
