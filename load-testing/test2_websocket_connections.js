/**
 * TEST 2: WebSocket Concurrency & Connection Stability
 * =====================================================
 * What this tests:
 *   - Can the server handle 500 simultaneous WebSocket connections?
 *   - How fast does connection establish?
 *   - Stay stable under 500 open connections held for 30s
 *
 * Run: k6 run test2_websocket_connections.js
 *
 * NOTE: Uses STOMP subprotocol header which Spring requires over raw WS.
 */

import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';

const connectTime    = new Trend('ws_connect_time_ms', true);
const stompSuccess   = new Rate('ws_stomp_success_rate');
const stompFailed    = new Counter('ws_stomp_failures');

export const options = {
  stages: [
    { duration: '10s', target: 50  },
    { duration: '20s', target: 200 },
    { duration: '30s', target: 500 },
    { duration: '30s', target: 500 },
    { duration: '10s', target: 0   },
  ],

  thresholds: {
    'ws_connect_time_ms':    ['p(95)<2000'],
    'ws_stomp_success_rate': ['rate>0.95'],
  },
};

export default function () {
  const userId = `ws_user_${__VU}`;

  // Spring requires v12.stomp subprotocol for raw WS STOMP
  const params = { subprotocols: ['v12.stomp'] };

  const stompConnect =
    'CONNECT\n' +
    'accept-version:1.2\n' +
    'host:localhost\n' +
    `login:${userId}\n` +
    'heart-beat:0,0\n' +
    '\n\0';

  const stompSubscribe =
    'SUBSCRIBE\n' +
    `id:sub-0\n` +
    `destination:/topic/notifications/${userId}\n` +
    'ack:auto\n' +
    '\n\0';

  const startTime = Date.now();

  const res = ws.connect(
    `ws://localhost:8080/ws-notify-raw`,
    params,
    function (socket) {
      socket.on('open', function () {
        connectTime.add(Date.now() - startTime);
        socket.send(stompConnect);
      });

      socket.on('message', function (data) {
        if (data.startsWith('CONNECTED') || data.includes('CONNECTED')) {
          stompSuccess.add(true);
          socket.send(stompSubscribe);
        }
      });

      socket.on('error', function (e) {
        stompFailed.add(1);
        stompSuccess.add(false);
      });

      sleep(30);
      socket.close();
    }
  );

  check(res, {
    '✅ WS HTTP upgrade (101)': (r) => r && r.status === 101,
  });
}

export function handleSummary(data) {
  const ct   = data.metrics['ws_connect_time_ms'];
  const rate = data.metrics['ws_stomp_success_rate'];
  const fail = data.metrics['ws_stomp_failures'];

  console.log('\n========== TEST 2 SUMMARY: WebSocket Concurrency ==========');
  if (ct) {
    console.log(`WS Connect time p50:  ${ct.values['p(50)'].toFixed(1)}ms`);
    console.log(`WS Connect time p95:  ${ct.values['p(95)'].toFixed(1)}ms`);
    console.log(`WS Connect time p99:  ${ct.values['p(99)'].toFixed(1)}ms`);
    console.log(`WS Connect time max:  ${ct.values['max'].toFixed(1)}ms`);
  }
  if (rate) console.log(`STOMP success rate:   ${(rate.values.rate * 100).toFixed(1)}%`);
  if (fail) console.log(`STOMP failures:       ${fail.values.count}`);
  console.log('===========================================================\n');
}
