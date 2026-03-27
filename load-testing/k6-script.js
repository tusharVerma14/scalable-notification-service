import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '15s', target: 50 },
    { duration: '30s', target: 50 },
    { duration: '15s', target: 0 },
  ],
};


export default function () {
  const userId = `loadtest_user_${__VU}`;
  
  const payload = JSON.stringify({
    targetUserId: userId,
    title: "Load Test Alert",
    body: "Stress testing the Redis Rate Limiter and RabbitMQ Conveyor Belt!",
    retryCount: 0
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-API-KEY': `key_${__VU}`
    },
  };


  const res = http.post('http://localhost:8080/api/v1/notify', payload, params);
  
  check(res, {
    'System responded correctly': (r) => r.status === 200 || r.status === 429,
    'No server errors':           (r) => r.status !== 500,
  });


  sleep(0.1); 
}
