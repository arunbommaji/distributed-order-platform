# Distributed Event-Driven Order Processing Platform

A production-ready microservices platform handling 50K+ async order transactions/day using choreography-based Saga pattern, Kafka, Redis, PostgreSQL, and Kubernetes.

## Architecture

```
Client  ──REST──►  Order Service  ──►  Kafka  ──►  Payment Service
                       │                    └──►  Inventory Service
                       │                    └──►  Notification Service
                       ◄── consumes payment-events + inventory-events
```

**Saga Flow (choreography)**
1. `POST /api/v1/orders` → Order Service persists order (PENDING), publishes `ORDER_CREATED`
2. Payment Service and Inventory Service consume `ORDER_CREATED` in **parallel**
3. Each publishes a success or failure event to its own topic
4. Order Service consumes both results → confirms or cancels the order
5. Notification Service consumes all terminal events → sends email/SMS

**Resilience patterns**
| Pattern | Implementation |
|---|---|
| Retry with backoff | `@RetryableTopic` (3 attempts, 1s×2 exponential) |
| Dead-letter queue | `*.DLT` Kafka topics per consumer group |
| Idempotency | Redis SETNX with 24h TTL on each consumer |
| Circuit breaker | Resilience4j on Payment Service |
| Optimistic locking | `@Version` on Product entity in Inventory |
| Zero-downtime deploy | K8s `RollingUpdate` with `maxUnavailable: 0` |

## Quick Start (Docker)

```bash
# 1. Start all infrastructure + services
docker compose up -d

# 2. Wait for services to be healthy (~30s)
docker compose ps

# 3. Place an order
curl -s -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "customer-001",
    "customerEmail": "arun@example.com",
    "items": [
      {"productId": "PROD-001", "productName": "Wireless Headphones", "quantity": 2, "unitPrice": 79.99},
      {"productId": "PROD-003", "productName": "USB-C Hub", "quantity": 1, "unitPrice": 49.99}
    ]
  }' | jq .

# 4. Poll order status
curl -s http://localhost:8081/api/v1/orders/<ORDER_ID> | jq .status
```

## Service Ports

| Service | Port | URL |
|---|---|---|
| Order Service | 8081 | http://localhost:8081 |
| Payment Service | 8082 | http://localhost:8082 |
| Inventory Service | 8083 | http://localhost:8083 |
| Notification Service | 8084 | http://localhost:8084 |
| Kafka UI | 8090 | http://localhost:8090 |

## API Reference

### Order Service

```
POST   /api/v1/orders                  Create order
GET    /api/v1/orders/{id}             Get order by ID
GET    /api/v1/orders?customerId=...   List orders by customer
GET    /actuator/health                Health check
```

### Sample Response

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "customerId": "customer-001",
  "customerEmail": "arun@example.com",
  "status": "CONFIRMED",
  "totalAmount": 209.97,
  "items": [
    { "productId": "PROD-001", "productName": "Wireless Headphones", "quantity": 2, "unitPrice": 79.99, "lineTotal": 159.98 },
    { "productId": "PROD-003", "productName": "USB-C Hub", "quantity": 1, "unitPrice": 49.99, "lineTotal": 49.99 }
  ],
  "createdAt": "2024-05-01T10:00:00",
  "updatedAt": "2024-05-01T10:00:01"
}
```

## Kafka Topics

| Topic | Producer | Consumers |
|---|---|---|
| `order-events` | order-service | payment-service, inventory-service, notification-service |
| `payment-events` | payment-service | order-service, notification-service |
| `inventory-events` | inventory-service | order-service, notification-service |
| `*-dlt` | Spring Kafka retry | manual inspection / alerting |

## Kubernetes Deploy

```bash
# Apply all manifests
kubectl apply -f k8s/ --recursive

# Check pod status
kubectl get pods -n order-platform

# Watch rolling deploy
kubectl rollout status deployment/order-service -n order-platform
```

## Project Structure

```
distributed-order-platform/
├── docker-compose.yml
├── .github/workflows/ci.yml       ← build → test → docker → k8s deploy
├── k8s/                           ← Kubernetes manifests
├── order-service/                 ← REST API + saga coordinator
├── payment-service/               ← Kafka consumer + Resilience4j CB
├── inventory-service/             ← Kafka consumer + optimistic locking
└── notification-service/          ← Multi-topic consumer
```

## Tech Stack

Java 17 · Spring Boot 3.2 · Spring Kafka · Spring Data JPA · Spring Data Redis · Resilience4j · PostgreSQL 16 · Redis 7 · Apache Kafka 7.6 · Docker · Kubernetes · GitHub Actions
