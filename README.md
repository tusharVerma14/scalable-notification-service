# Real-Time Notification Server

A production-ready notification system built with Spring Boot. It delivers messages via WebSocket (STOMP) to online users and persists undelivered messages to MySQL for automatic re-delivery upon reconnection.

---

## Architecture Flow

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
- AI-Powered: Integrates Google Gemini via Spring AI to automatically generate personalized notification content from brief contexts.

---

## Performance Benchmarks

All tests were performed on an **Apple M4 Pro (12-core)** with 24GB RAM, using **k6** load-testing scripts.

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

## Getting Started

### 1. Start Infrastructure
```bash
docker-compose up -d
```

### 2. Start the Server
```bash
./gradlew bootRun
```
The server will be available at http://localhost:8080.

---

## API Documentation

### Send Notification
`POST /api/v1/notify`

**Request Body:**
```json
{
  "targetUserId": "user_123",
  "title": "Order Update",
  "body": "Your order has been shipped!",
  "channels": ["WEBSOCKET"]
}
```

### Send AI-Enhanced Notification
`POST /api/v1/notify/ai-enhance`

**Request Body:**
```json
{
  "targetUserId": "user_123",
  "event": "Order Delayed",
  "context": "Due to heavy rain in the area",
  "channels": ["WEBSOCKET"]
}
```
*Uses Google Gemini to automatically generate a friendly, personalized title and body for the notification before queuing it for delivery.*

---

## License
MIT
