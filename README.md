# Real-Time Notification Server

A production-ready notification system built with Spring Boot. It delivers messages via WebSocket (STOMP) to online users and persists undelivered messages to MySQL for automatic re-delivery upon reconnection.

---

## Architecture

```
REST API (Post/api/v1/notify)
        |
        v
------------------
|  Rate Limiter  |  (Redis Token Bucket - 1000 req/min/key)
------------------
        |
        v
------------------     -----------------
|    RabbitMQ    |---->|  Dead Letter  |
|   Main Queue   |     |  Queue (DLQ)  |
------------------     -----------------
        |
        v
------------------
|  Notification  |  (Strategy Pattern Support)
|    Consumer    |
------------------
        |
   Check Redis: Is user online?
        |
   +----+--------------------------+
   |                               |
   v                               v
 ONLINE                         OFFLINE
 Push WS                  Save to MySQL (PENDING)
   |                               |
   v                      User Re-connects
Delivered OK              +--------v---------+
                          | Pending Service  |
                          +--------|---------+
                                   |
                          Push to WS Deliver
```

---

## Technical Features

- Real-time delivery via WebSocket (STOMP) with SockJS fallback.
- Distributed rate limiting using Redis-backed Token Bucket algorithm.
- Message queuing with RabbitMQ for high-throughput, non-blocking ingestion.
- Guaranteed delivery: offline notifications are persisted and delivered on reconnect.
- Reliability: includes a Dead Letter Queue (DLQ) for failed delivery tracking.
- Performance: leverages Java 21 Virtual Threads (Project Loom) for scalability.
- Extensibility: designed using the Strategy pattern for adding new channels (Sms, Email).

---

## Tech Stack

- Backend: Spring Boot 4.x, Java 21
- Messaging: STOMP over WebSocket
- Queue: RabbitMQ 3.x
- Cache/State: Redis 7.x
- Database: MySQL 8.x
- Testing: k6 (Load Testing)
- Deployment: Docker & Docker Compose


## Performance Benchmarks

All tests were performed on an Single Instance machine (12-core)** with 24GB RAM, using **k6** load-testing scripts.

### 1. REST Throughput
*   **Metric:** Raw ingestion of notification requests.
*   **Result:** **937 requests/second** sustained.
*   **Latency (p95):** **20.3ms**.
*   **Outcome:** 0 errors across 140,000+ requests.

### 2. WebSocket Stability 
*   **Metric:** Active concurrent session management.
*   **Result:** **500 simultaneous** stable connections.
*   **Handshake:** ~1ms connection establishment (TCP/WS Upgrade).

### 3. End-to-End Delivery
*   **Metric:** Total time from API call to client reception.
*   **Performance (p50):** **11.2ms**.
*   **Performance (p95):** **49.8ms**.

### 4. Spike Resilience
*   **Metric:** Sudden traffic burst resilience.
*   **Scenario:** 0 → 500 concurrent users in 5 seconds.
*   **Outcome:** **0% error rate**; RabbitMQ smoothly buffered the surge.
*   **p99 Latency:** 365ms during peak peak burst.

---

## Project Structure

```
notification-server/
├── src/main/java/com/techgiant/notification/
│   ├── config/              # WebSocket and RabbitMQ configuration
│   ├── controller/          # REST endpoints and WS event listeners
│   ├── dto/                 # Request/Response payloads
│   ├── model/               # Persistence entities
│   ├── repository/          # JPA repositories
│   └── service/             # Business logic and delivery strategies
├── load-testing/            # K6 load testing scripts
├── docker-compose.yml       # Infrastructure orchestration
└── build.gradle             # Build configuration
```

---

## Setup and Installation

### 1. Infrastructure
Ensure Docker is running and execute:
```bash
docker-compose up -d
```
This starts MySQL (3306), Redis (6379), and RabbitMQ (5672/15672).

### 2. Application
Run the Spring Boot application:
```bash
./gradlew bootRun
```
The server will be available at http://localhost:8080.

---

## API Documentation

### Send Notification
`POST /api/v1/notify`

Headers:
- Content-Type: application/json
- X-API-KEY: YOUR_API_KEY (optional, uses default if missing)

Payload:
```json
{
  "targetUserId": "user_123",
  "title": "Welcome",
  "body": "Hello world from the notification server",
  "channels": ["WEBSOCKET"]
}
```

### Endpoints
| URL | Use |
|---|---|
| `ws://host/ws-notify` | Browser clients (SockJS + STOMP) |
| `ws://host/ws-notify-raw` | Raw WebSocket (load testing, programmatic) |

### STOMP Headers on CONNECT
```
userId: <your-user-id>
```

### Subscribe to notifications
```
SUBSCRIBE /topic/notifications/{userId}
```

---

## 🔧 Switching Rate Limit Strategy

Default: **Token Bucket** (smooth, production-grade).  
To switch to Fixed Window: change `@Primary` from `TokenBucketRateLimitStrategy` to `FixedWindowRateLimitStrategy`. Zero other changes needed.

## ➕ Adding a New Delivery Channel (e.g. Email)

1. Create `EmailDeliveryStrategy implements DeliveryStrategy`
2. Annotate with `@Service`
3. Implement `getChannelType()` → return `"EMAIL"`
4. Implement `deliver(Notification n)`
5. Done — Spring auto-discovers it, Consumer routes to it automatically

---

## 📄 License

MIT
