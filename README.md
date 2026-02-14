# MT Statement Processor

Spring Boot 3 (Java 17) service for processing SWIFT MT940/941/942/950 statements.

## Architecture

```
MQ Inbound → MqIngestionStrategy → ODS (audit) → Parser → Aggregation/Marshalling
                                                                ↓ complete
                                                         Statement Routing
                                                           ↓ pref match      ↓ no match
                                                        Delivery           (warning logged)
                                                           ↓
                                                     Downstream + SWIFT Relay

Rule CSV → Rule Loader → routing_rule table
CRUD API → routing_rule + relay_config
```

### Pipeline Stages

1. **MQ Ingestion** — Receives raw SWIFT messages from MQ inbound queue, saves to ODS for audit, then processes through the pipeline. (POC simulates MQ via direct method call; production would use `@JmsListener`)
2. **Aggregation/Marshalling** — Multi-page statements collected until all pages arrive (configurable expiry). Single-page statements pass through immediately. Duplicate pages detected via SHA-256 checksum
3. **Statement Routing** — Evaluates preference rules (account, message type, sender BIC, receiver BIC — all support `*` wildcard). Unmatched messages log a warning with no delivery
4. **Delivery** — Sends to downstream queues + optional SWIFT relay (based on relay config) with retry

## Quick Start

```bash
./mvnw spring-boot:run
```

The application starts on **port 8080** with a file-based H2 database (`./data/mtdb`).
Sample data is loaded automatically via Flyway migrations.

### Demo Dashboard

Open **http://localhost:8080/** in a browser to access the demo UI with:

- Pipeline visualization (Marshalled → Routed → Delivered)
- Submit MT messages via textarea
- View aggregation status and deliveries
- CRUD management for routing preferences and relay config
- Auto-refresh every 3 seconds (with pause/resume toggle)

### Sample Messages

Open **http://localhost:8080/samples.html** for pre-built test scenarios:

| Scenario | Sender BIC | Receiver BIC | Account | Expected Route | Relay |
|---|---|---|---|---|---|
| Single-page MT940 | HSBCGB2L | CITIUS33 | 123456789 | RECON.INTELLIMATCH.IN | No |
| Multi-page MT940 (2 pages) | HSBCGB2L | CITIUS33 | 123456789 | RECON.INTELLIMATCH.IN | No |
| MT942 Intraday | DEUTDEFF | BNPAFRPP | 987654321 | CASH.CALYPSO.INTRADAY | Yes |
| MT950 Statement | HSBCGB2L | CITIUS33 | 123456789 | GL.SAP.STMT.FEED | No |
| MT941 Balance Report | COBADEFF | SOGEFRPP | 555555555 | ARCHIVE.COMPLI.STORE | No |
| No Matching Rule | CHASGB2L | NWBKGB2L | 000000000 | None | No |
| Duplicate Page | HSBCGB2L | CITIUS33 | 123456789 | Rejected | No |

## Verify End-to-End Flow

Submit a sample message and check deliveries:

```bash
# Submit a message
curl -X POST http://localhost:8080/test/ods-messages \
  -H "Content-Type: application/json" \
  -d '{"rawMessage":"{1:F01HSBCGB2LAXXX0000000000}{2:I940CITIUS33XXXXN}{4:\n:20:TESTREF\n:25:123456789\n:28C:00001/001\n:60F:C210301EUR2000,\n:62F:C210301EUR1850,\n-}"}'

# Check deliveries
curl http://localhost:8080/test/deliveries | python3 -m json.tool
```

You should see a delivery to `RECON.INTELLIMATCH.IN`.

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
  -d '{"accountNumber":"999999","messageType":"MT950","senderBic":"*","receiverBic":"HSBCGB2L","destinationQueue":"NEW.Q1","active":true}'

# Add relay config for Deutsche Bank → BNP Paribas
curl -u admin:admin123 -X POST http://localhost:8080/api/relay-config \
  -H "Content-Type: application/json" \
  -d '{"accountNumber":"987654321","senderBic":"DEUTDEFF","receiverBic":"BNPAFRPP","active":true}'

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
| mt.ingestion.mode                 | MQ              | MQ ingestion mode                   |
| mt.ingestion.mq.inbound-queue    | MT.INBOUND      | MQ inbound queue name               |
| mt.aggregation.expiry-minutes     | 2               | Multi-page timeout (minutes)        |
| mt.delivery.mode                  | MOCK            | MOCK or MQ                          |
| mt.routing.rules-file-path        | classpath       | Path to CSV rules file              |

## Metrics

Available at `/actuator/metrics`:

- `mt.ingestion.processed` — messages ingested
- `mt.aggregation.completed` — aggregations completed
- `mt.aggregation.rejected` — aggregations rejected/expired
- `mt.routing.cache.hit` — routing rule cache hits
- `mt.delivery.success` — successful deliveries
- `mt.delivery.failure` — failed deliveries
