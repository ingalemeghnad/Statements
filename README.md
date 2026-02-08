# MT Statement Processor

Spring Boot 3 (Java 17) service for processing SWIFT MT940/941/942/950 statements.

## Architecture

```
ODS → Ingestion (Polling/Event) → Parser → Aggregation → Routing → Delivery
                                           ↘ Scheduler (expiry)
Rule CSV → Rule Loader → routing_rule table
CRUD API → routing_rule + relay_config tables
```

## Quick Start

```bash
./mvnw spring-boot:run
```

The application starts on **port 8080** with an in-memory H2 database.
Sample data is loaded automatically via Flyway migrations.

## Verify End-to-End Flow

After startup, the polling ingestion picks up sample ODS messages within 5 seconds.
Check delivered messages:

```bash
curl http://localhost:8080/test/deliveries | python3 -m json.tool
```

You should see deliveries to `REPORTING.Q1` and `SWIFT.RELAY`.

## API Endpoints

### Test (no auth required)

| Method | Path               | Description              |
|--------|--------------------|--------------------------|
| GET    | /test/deliveries   | View mock deliveries     |
| DELETE | /test/deliveries   | Clear mock deliveries    |

### Routing Rules (Basic Auth: admin/admin123)

| Method | Path                   | Description          |
|--------|------------------------|----------------------|
| GET    | /api/routing-rules     | List all rules       |
| POST   | /api/routing-rules     | Create a rule        |
| PUT    | /api/routing-rules/{id}| Update a rule        |
| DELETE | /api/routing-rules/{id}| Delete a rule        |

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

# Create a routing rule
curl -u admin:admin123 -X POST http://localhost:8080/api/routing-rules \
  -H "Content-Type: application/json" \
  -d '{"accountNumber":"999999","messageType":"MT950","destinationQueue":"NEW.Q1","active":true}'

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

| Property                          | Default   | Description                    |
|-----------------------------------|-----------|--------------------------------|
| mt.ingestion.mode                 | POLLING   | POLLING or EVENT               |
| mt.ingestion.polling.batch-size   | 200       | Messages per poll              |
| mt.ingestion.polling.interval-ms  | 5000      | Poll interval                  |
| mt.aggregation.expiry-minutes     | 60        | Multi-page timeout             |
| mt.delivery.mode                  | MOCK      | MOCK or MQ                     |
| mt.routing.rules-file-path        | classpath | Path to CSV rules file         |

## Metrics

Available at `/actuator/metrics`:

- `mt.ingestion.processed` — messages ingested
- `mt.aggregation.completed` — aggregations completed
- `mt.aggregation.rejected` — aggregations rejected/expired
- `mt.routing.cache.hit` — routing rule cache hits
- `mt.delivery.success` — successful deliveries
- `mt.delivery.failure` — failed deliveries

what bic/branches statements processor should consider or exclude? all excluding some branches including XXX?
statement relay - what happens if it cant find config. what are rules for this in routing.

