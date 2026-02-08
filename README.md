# MT Statement Processor

Spring Boot 3 (Java 17) service for processing SWIFT MT940/941/942/950 statements.

## Architecture

```
ODS → Ingestion (Polling/Event) → Parser → Aggregation/Marshalling Filter
                                                ↓ passes filter         ↓ skipped
                                           Aggregation          ────────┐
                                                ↓ complete              ↓
                                     Statement Routing ←────────────────┘
                                       ↓ pref match      ↓ no match     ↓ branch excluded
                                    Delivery      Exception Queue       (skipped)
                                       ↓
                                  Downstream + SWIFT Relay

Rule CSV → Rule Loader → routing_rule table
CRUD API → routing_rule + relay_config + aggregation_bic_filter + routing_bic_exclusion
```

### Pipeline Stages

1. **Ingestion** — Polls ODS table for NEW messages, marks PROCESSING, parses raw SWIFT
2. **Aggregation/Marshalling Filter** — Checks receiver BIC against allowed list + branch exclusions. Messages that don't pass skip aggregation and go directly to statement routing
3. **Aggregation** — Multi-page statements collected until all pages arrive (1-hour expiry). Single-page statements pass through immediately
4. **Statement Routing** — Evaluates preference rules (account, message type, sender BIC, receiver BIC — all support `*` wildcard). Branch exclusions skip routing entirely. Unmatched messages go to exception queue
5. **Delivery** — Sends to downstream queues + optional SWIFT relay with retry

## Quick Start

```bash
./mvnw spring-boot:run
```

The application starts on **port 8080** with an in-memory H2 database.
Sample data is loaded automatically via Flyway migrations.

### Demo Dashboard

Open **http://localhost:8080/** in a browser to access the demo UI with:

- Pipeline visualization with live counts
- Submit MT messages via textarea
- View ODS messages, aggregation status, and deliveries
- CRUD management for all configuration (routing preferences, relay config, BIC filters, branch exclusions)
- Auto-refresh every 3 seconds (with pause/resume toggle)

## Verify End-to-End Flow

After startup, the polling ingestion picks up sample ODS messages within 5 seconds.
Check delivered messages:

```bash
curl http://localhost:8080/test/deliveries | python3 -m json.tool
```

You should see deliveries to `REPORTING.Q1` and `SWIFT.RELAY`.

## API Endpoints

### Test (no auth required)

| Method | Path                    | Description                  |
|--------|-------------------------|------------------------------|
| GET    | /test/deliveries        | View mock deliveries         |
| DELETE | /test/deliveries        | Clear mock deliveries        |
| GET    | /test/ods-messages      | List all ODS messages        |
| POST   | /test/ods-messages      | Submit a raw MT message      |
| GET    | /test/ods-messages/stats| ODS status counts            |
| GET    | /test/aggregations      | List all aggregation records |

### Statement Routing Preferences (Basic Auth: admin/admin123)

| Method | Path                   | Description          |
|--------|------------------------|----------------------|
| GET    | /api/routing-rules     | List all rules       |
| POST   | /api/routing-rules     | Create a rule        |
| PUT    | /api/routing-rules/{id}| Update a rule        |
| DELETE | /api/routing-rules/{id}| Delete a rule        |

Routing rules match on `accountNumber`, `messageType`, `senderBic`, `receiverBic`. All fields support `*` wildcard or null/blank for "match any".

### Relay Config (Basic Auth: admin/admin123)

| Method | Path                  | Description           |
|--------|-----------------------|-----------------------|
| GET    | /api/relay-config     | List all configs      |
| POST   | /api/relay-config     | Create a config       |
| PUT    | /api/relay-config/{id}| Update a config       |
| DELETE | /api/relay-config/{id}| Delete a config       |

### Aggregation/Marshalling BIC Filters (Basic Auth: admin/admin123)

| Method | Path                       | Description           |
|--------|----------------------------|-----------------------|
| GET    | /api/aggregation-filters   | List all filters      |
| POST   | /api/aggregation-filters   | Create a filter       |
| DELETE | /api/aggregation-filters/{id}| Delete a filter     |

Filter types:
- `ALLOWED_BIC` — 8-char receiver BICs that should go through aggregation
- `EXCLUDED_BRANCH` — Branch suffixes (last 3 chars of 11-char BIC) to skip aggregation

### Statement Routing Branch Exclusions (Basic Auth: admin/admin123)

| Method | Path                       | Description              |
|--------|----------------------------|--------------------------|
| GET    | /api/routing-exclusions    | List all exclusions      |
| POST   | /api/routing-exclusions    | Create an exclusion      |
| DELETE | /api/routing-exclusions/{id}| Delete an exclusion     |

Branch codes (last 3 chars of full BIC) that skip statement routing entirely.

### Rule Loader (Basic Auth: admin/admin123)

| Method | Path              | Description                   |
|--------|-------------------|-------------------------------|
| POST   | /api/rules/reload | Reload rules from CSV file    |

### Other

| Path            | Description            |
|-----------------|------------------------|
| /h2-console     | H2 database console    |
| /actuator       | Actuator endpoints     |

## Example API Calls

```bash
# List routing rules
curl -u admin:admin123 http://localhost:8080/api/routing-rules

# Create a routing rule with wildcard sender BIC
curl -u admin:admin123 -X POST http://localhost:8080/api/routing-rules \
  -H "Content-Type: application/json" \
  -d '{"accountNumber":"999999","messageType":"MT950","senderBic":"*","receiverBic":"BANKGB22","destinationQueue":"NEW.Q1","active":true}'

# Add allowed BIC for aggregation
curl -u admin:admin123 -X POST http://localhost:8080/api/aggregation-filters \
  -H "Content-Type: application/json" \
  -d '{"bicValue":"BANKGB22","filterType":"ALLOWED_BIC","active":true}'

# Add excluded branch for aggregation
curl -u admin:admin123 -X POST http://localhost:8080/api/aggregation-filters \
  -H "Content-Type: application/json" \
  -d '{"bicValue":"XXX","filterType":"EXCLUDED_BRANCH","active":true}'

# Add branch exclusion for routing
curl -u admin:admin123 -X POST http://localhost:8080/api/routing-exclusions \
  -H "Content-Type: application/json" \
  -d '{"branchCode":"LON","active":true}'

# Submit a new message to ODS
curl -X POST http://localhost:8080/test/ods-messages \
  -H "Content-Type: application/json" \
  -d '{"rawMessage":"{1:F01BANKGB22AXXX0000000000}{2:I940CLIENTBICXXXXN}{4:\n:20:TESTREF\n:25:123456789\n:28C:00001/001\n:60F:C210301EUR2000,\n:62F:C210301EUR1850,\n-}"}'

# Reload rules from CSV
curl -u admin:admin123 -X POST http://localhost:8080/api/rules/reload

# Check deliveries
curl http://localhost:8080/test/deliveries
```

## Running Tests

```bash
./mvnw test
```

## Configuration

Key properties in `application.yml`:

| Property                          | Default         | Description                         |
|-----------------------------------|-----------------|-------------------------------------|
| mt.ingestion.mode                 | POLLING         | POLLING or EVENT                    |
| mt.ingestion.polling.batch-size   | 200             | Messages per poll                   |
| mt.ingestion.polling.interval-ms  | 5000            | Poll interval                       |
| mt.aggregation.expiry-minutes     | 60              | Multi-page timeout                  |
| mt.delivery.mode                  | MOCK            | MOCK or MQ                          |
| mt.routing.rules-file-path        | classpath       | Path to CSV rules file              |
| mt.routing.exception-queue        | EXCEPTION.QUEUE | Queue for unroutable messages       |

Aggregation BIC filters and routing branch exclusions are managed via DB (CRUD API + UI), not application properties.

## Metrics

Available at `/actuator/metrics`:

- `mt.ingestion.processed` — messages ingested
- `mt.aggregation.completed` — aggregations completed
- `mt.aggregation.rejected` — aggregations rejected/expired
- `mt.routing.cache.hit` — routing rule cache hits
- `mt.routing.exception.queue` — messages sent to exception queue
- `mt.routing.skipped` — messages skipped due to branch exclusion
- `mt.delivery.success` — successful deliveries
- `mt.delivery.failure` — failed deliveries
