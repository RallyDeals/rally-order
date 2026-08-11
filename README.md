# Rally Order Service

Order Service owns orders for both **NORMAL** (direct cart checkout) and
**DEAL** (group-buy) purchases within the RallyDeals platform. It is the only
service that calls Payment Service, and it publishes/consumes events to
coordinate with Catalog, Inventory, Deal, and Payment services.

---

## Tech Stack

- **Java / Spring Boot**
- **PostgreSQL** — persistence, managed via Flyway migrations
- **Apache Kafka** — event-driven communication with other services
- **Flyway** — versioned DB schema migrations
- **Spring Security** — authentication is handled upstream by the gateway, which forwards the authenticated user's ID via the `X-User-Id` header
- **springdoc-openapi** — API documentation

---

## Project Structure

```
com.company.orderservice
├── OrderServiceApplication.java
├── config/              # Kafka, Jackson, scheduling, security
├── api/                 # REST controllers, DTOs, mappers
├── domain/               # Order, OrderProduct, OrderStatus, OrderType, CancelReason
├── application/          # one class per consumer/use-case
├── infrastructure/
│   ├── persistence/       # repositories
│   ├── messaging/
│   │   ├── producer/       # OutboxEventPublisher, OutboxRelay
│   │   ├── consumer/       # one Kafka listener per subscribed event
│   │   └── dedup/          # ProcessedEventsGuard
│   └── client/             # CatalogServiceClient, InventoryServiceClient, DealServiceClient
├── scheduling/            # sweep jobs
└── exception/             # additions beyond the shared module
```

---

## Local Development Setup

### Prerequisites

- JDK (17+)
- Maven
- Docker + Docker Compose

### 1. Start supporting infrastructure

Postgres, Kafka, and Kafdrop (Kafka UI) run via Docker Compose:

```bash
docker compose up -d postgres broker kafdrop
```

| Service   | Port (host) | Notes                          |
|-----------|-------------|---------------------------------|
| Postgres  | `5433`      | maps to container's `5432`      |
| Kafka     | `9092`      | broker, plaintext                |
| Kafdrop   | `9000`      | Kafka UI — http://localhost:9000 |

> The `order-service` block in `docker-compose.yml` is currently commented
> out — the app is run locally (IDE / `mvnw spring-boot:run`) against these
> containers rather than containerized itself, until there's app logic worth
> containerizing.

To resume containers after a restart (no config changes):

```bash
docker compose start
```

Use `docker compose up -d` again only if the compose file changed.

### 2. Configure environment

`src/main/resources/application.properties` already points at the local
containers:

```properties
spring.application.name=rally-order
server.port=8010
spring.datasource.url=jdbc:postgresql://localhost:5433/order_db
spring.datasource.username=user
spring.datasource.password=password
spring.datasource.driver-class-name=org.postgresql.Driver

spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true

rally.jwt.secret=${JWT_SECRET}
```

Set the `JWT_SECRET` environment variable locally before running the app.

### 3. Run migrations & start the app

Flyway migrations run automatically on startup:

```bash
./mvnw spring-boot:run
```

The app boots on **http://localhost:8010**.

### 4. API docs

Once running, OpenAPI/Swagger UI is available at:

```
http://localhost:8010/swagger-ui.html
```

---

## Database

Schema is managed exclusively through **Flyway** migrations under
`src/main/resources/db/migration`, named `V<n>__description.sql`.

- **Never edit an applied migration** — add a new versioned file instead.
- Key tables: `orders`, `order_products`, `processed_events`, `outbox_events`.
- `orders` enforces order lifecycle via CHECK constraints on `order_type`,
  `status`, and DEAL-field consistency, plus optimistic locking via
  `version`.

---

## Messaging

Order Service uses a **transactional outbox** + **inbound dedup guard**
pattern for reliable event delivery:

- **Outbox relay** — polls `outbox_events` for unpublished rows and
  publishes them to Kafka.
- **Dedup guard** — checks `processed_events` before processing any inbound
  event, inserted in the same transaction as the business write.

**Publishes:** `order.payment_initiation_requested`,
`order.payment_settlement_requested`, `order.created`, `order.authorized`,
`order.normal_order_cancelled`, `order.deal_order_cancelled`

**Consumes:** `payment.authorized`, `payment.charged`, `payment.captured`,
`payment.failed`, `payment.voided`, `participant.joined`, `participant.left`,
`deal.succeeded`, `deal.failed`

