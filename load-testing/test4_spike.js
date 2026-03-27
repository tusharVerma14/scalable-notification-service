// Spike Test - Sudden traffic burst
// Ramps from 0 to 500 users in 5 seconds to test system resilience.


import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Counter, Trend } from 'k6/metrics';

const errorRate      = new Rate('spike_error_rate');
const responseTime   = new Trend('spike_response_ms', true);
const successCount   = new Counter('spike_success');
const rateLimitCount = new Counter('spike_rate_limited');

export const options = {
  stages: [
    // Normal traffic
    { duration: '10s', target: 10  },
    { duration: '20s', target: 10  },

    // SPIKE! 0 → 500 users in 5 seconds
    { duration: '5s',  target: 500 },
    // Hold the spike
    { duration: '20s', target: 500 },

    // Spike ends — back to normal
    { duration: '5s',  target: 10  },
    // Recovery period: check system is still healthy
    { duration: '20s', target: 10  },
    { duration: '5s',  target: 0   },
  ],

  thresholds: {
    // Even during a spike, p99 must be under 3 seconds
    'spike_response_ms': ['p(99)<3000'],
    // Error rate must stay below 5%
    'spike_error_rate':  ['rate<0.05'],
  },
};

export default function () {
  const payload = JSON.stringify({
    targetUserId: `spike_user_${__VU}`,
    title:        'Spike Test',
    body:         `Spike burst — VU ${__VU} iteration ${__ITER}`,
    channels:     ['WEBSOCKET'],
    retryCount:   0,
  });

  const start = Date.now();
  const res = http.post('http://localhost:8080/api/v1/notify', payload, {
    headers: {
      'Content-Type': 'application/json',
      'X-API-KEY':    `spike_key_${__VU}`,
    },
    timeout: '10s',
  });

  const elapsed = Date.now() - start;
  responseTime.add(elapsed);

  if (res.status === 200) {
    successCount.add(1);
    errorRate.add(false);
  } else if (res.status === 429) {
    rateLimitCount.add(1);
    errorRate.add(false); // rate limiting is EXPECTED during spike
  } else {
    errorRate.add(true);
    console.error(`Spike test got ${res.status} — ${res.body}`);
  }

  check(res, {
    'No server errors':          (r) => r.status !== 500,
    'Response under 3 seconds':  ()  => elapsed < 3000,
  });


  sleep(0.05); // Very short think time to simulate burst
}

export function handleSummary(data) {
  const rt   = data.metrics['spike_response_ms'];
  const err  = data.metrics['spike_error_rate'];
  const succ = data.metrics['spike_success'];
  const lim  = data.metrics['spike_rate_limited'];

  console.log('\n========== TEST 4 SUMMARY: Spike Test ==========');
  if (rt) {
    console.log(`Response p50:  ${rt.values['p(50)'].toFixed(1)}ms`);
    console.log(`Response p90:  ${rt.values['p(90)'].toFixed(1)}ms`);
    console.log(`Response p99:  ${rt.values['p(99)'].toFixed(1)}ms`);
    console.log(`Response max:  ${rt.values['max'].toFixed(1)}ms`);
  }
  if (err) console.log(`Error rate:         ${(err.values.rate * 100).toFixed(2)}%`);
  console.log(`Successes (200):    ${succ ? succ.values.count : 0}`);
  console.log(`Rate limited (429): ${lim  ? lim.values.count  : 0}`);
  console.log('=================================================\n');
}
