/**
 * TEST 3: End-to-End Notification Delivery (REST → RabbitMQ → WebSocket)
 * ========================================================================
 * What this tests:
 *   - Full real-world scenario: user is connected via WebSocket, someone
 *     sends them a notification via REST → does it arrive? How fast?
 *   - Measures end-to-end delivery latency (from HTTP POST to WS message)
 *   - Tests concurrent send + receive at the same time
 *
 * How it works:
 *   - One "group" of VUs acts as RECEIVERS (open WebSocket, wait for notifs)
 *   - Another "group" acts as SENDERS (call REST API to send notifications)
 *   - We measure how long the round trip takes
 *
 * Run: k6 run test3_e2e_delivery.js
 */

import http from 'k6/http';
import ws   from 'k6/ws';
import { check, sleep, group } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const e2eDeliveryTime  = new Trend('e2e_delivery_latency_ms', true);
const deliveredCount   = new Counter('notifications_delivered_e2e');
const sendSuccess      = new Counter('notifications_sent_ok');

export const options = {
  scenarios: {
    // Scenario A: WebSocket receivers — 100 users hold connections open
    ws_receivers: {
      executor:       'constant-vus',
      vus:             100,
      duration:        '90s',
      exec:            'wsReceiver',
    },

    // Scenario B: REST senders — 50 users hammer the REST API
    rest_senders: {
      executor:       'ramping-vus',
      startVUs:        0,
      stages: [
        { duration: '10s', target: 10  },
        { duration: '30s', target: 50  },
        { duration: '30s', target: 50  },
        { duration: '20s', target: 0   },
      ],
      exec:            'restSender',
      startTime:       '5s', // Senders start 5s after receivers connect
    },
  },

  thresholds: {
    // End-to-end delivery should be under 1s for 95% of notifications
    'e2e_delivery_latency_ms': ['p(95)<1000'],
  },
};

/**
 * RECEIVER: opens WebSocket and listens for incoming notifications
 */
export function wsReceiver() {
  const userId = `e2e_user_${__VU}`;

  const stompConnect =
    'CONNECT\n' +
    'accept-version:1.1,1.2\n' +
    `userId:${userId}\n` +
    '\n\0';

  const stompSubscribe =
    'SUBSCRIBE\n' +
    `id:sub-${__VU}\n` +
    `destination:/topic/notifications/${userId}\n` +
    'ack:auto\n' +
    '\n\0';

  ws.connect(`ws://localhost:8080/ws-notify-raw`, null, function (socket) {
    socket.on('open', () => socket.send(stompConnect));

    socket.on('message', (data) => {
      if (data.startsWith('CONNECTED')) {
        socket.send(stompSubscribe);
      }

      // A real notification arrived!
      if (data.includes('"title"')) {
        deliveredCount.add(1);
        // We embed the send timestamp in the notification body
        // so we can calculate how long end-to-end delivery took
        try {
          const frameBody = data.substring(data.indexOf('{'));
          const payload   = JSON.parse(frameBody.replace(/\0$/, ''));
          if (payload.body && payload.body.startsWith('ts:')) {
            const sentAt  = parseInt(payload.body.replace('ts:', ''));
            const elapsed = Date.now() - sentAt;
            e2eDeliveryTime.add(elapsed);
          }
        } catch (_) {}
      }
    });

    sleep(85); // Stay connected for the bulk of the test
    socket.close();
  });
}

/**
 * SENDER: calls REST API to send notifications to receiver users
 */
export function restSender() {
  // Send to a receiver VU (VU IDs 1-100 are receivers)
  const targetUserId = `e2e_user_${Math.ceil(Math.random() * 100)}`;
  const sendTimestamp = Date.now();

  const payload = JSON.stringify({
    targetUserId: targetUserId,
    title:        'E2E Test Notification',
    body:         `ts:${sendTimestamp}`,  // embed send time so receiver can calc latency
    channels:     ['WEBSOCKET'],
    retryCount:   0,
  });

  const res = http.post('http://localhost:8080/api/v1/notify', payload, {
    headers: {
      'Content-Type': 'application/json',
      'X-API-KEY':    `sender_key_${__VU}`,
    },
    timeout: '5s',
  });

  if (res.status === 200) sendSuccess.add(1);

  check(res, {
    '✅ Notification sent (200)': (r) => r.status === 200,
  });

  sleep(0.5);
}

export function handleSummary(data) {
  const e2e  = data.metrics['e2e_delivery_latency_ms'];
  const dlvd = data.metrics['notifications_delivered_e2e'];
  const sent = data.metrics['notifications_sent_ok'];

  console.log('\n========== TEST 3 SUMMARY: End-to-End Delivery ==========');
  if (e2e) {
    console.log(`E2E latency p50:  ${e2e.values['p(50)'].toFixed(1)}ms`);
    console.log(`E2E latency p90:  ${e2e.values['p(90)'].toFixed(1)}ms`);
    console.log(`E2E latency p95:  ${e2e.values['p(95)'].toFixed(1)}ms`);
    console.log(`E2E latency max:  ${e2e.values['max'].toFixed(1)}ms`);
  }
  console.log(`Notifications sent:      ${sent ? sent.values.count : 0}`);
  console.log(`Notifications delivered: ${dlvd ? dlvd.values.count : 0}`);
  console.log('==========================================================\n');
}
