# MT Statement Processor Onboarding

This guide is the fastest path to understand and test the project.

## 1) What This Service Does

It processes incoming SWIFT MT statement messages (MT940/941/942/950) through:

1. Ingestion from ODS table (`NEW` -> `PROCESSING`)
2. Parsing raw SWIFT into structured fields
3. Aggregation filter (whether to aggregate or bypass)
4. Multi-page aggregation (or direct pass-through for single page)
5. Routing (rules + exclusions + exception queue fallback)
6. Delivery (downstream queue(s) + optional SWIFT relay)

## 2) Architecture At A Glance

Main sequence:

`ODS -> Ingestion -> Parser -> Aggregation Filter -> Aggregation -> Routing -> Delivery`

Key policy:

- For marshalling/routing exclusions, `messageType = null/blank/*` means "all MT types".
- Explicit value like `MT942` means type-specific exclusion.

## 3) Read Order (2-hour walkthrough)

Open files in this order:

1. `README.md`
2. `src/main/java/com/bank/mt/ingestion/PollingOdsIngestionStrategy.java`
3. `src/main/java/com/bank/mt/parsing/MtParser.java`
4. `src/main/java/com/bank/mt/aggregation/AggregationFilter.java`
5. `src/main/java/com/bank/mt/aggregation/AggregationService.java`
6. `src/main/java/com/bank/mt/routing/RoutingService.java`
7. `src/main/java/com/bank/mt/delivery/DeliveryService.java`
8. `src/main/resources/static/index.html`
9. `src/test/java/com/bank/mt/EndToEndIntegrationTest.java`

## 4) Data Model Cheat Sheet

Core tables:

- `mt_message_ods`: raw incoming messages and status lifecycle
- `mt_aggregation`: aggregation group header
- `mt_aggregation_page`: individual pages linked to aggregation
- `routing_rule`: statement routing preferences
- `relay_config`: relay eligibility rules
- `aggregation_bic_filter`: marshalling filters (allowed/excluded)
- `routing_bic_exclusion`: routing branch exclusions (with optional message type)

Statuses:

- ODS: `NEW`, `PROCESSING`, `COMPLETED`, `FAILED`
- Aggregation: `IN_PROGRESS`, `COMPLETED`, `REJECTED`

## 5) Runbook (Hands-on)

Start app:

```bash
./mvnw spring-boot:run
```

Open dashboard:

- `http://localhost:8080/`

Useful endpoints:

- `GET /test/ods-messages`
- `GET /test/deliveries`
- `GET /test/aggregations`
- `GET /test/ods-messages/stats`

Submit message quickly:

```bash
curl -X POST http://localhost:8080/test/ods-messages \
  -H "Content-Type: application/json" \
  -d '{"rawMessage":"{1:F01BANKGB22AXXX0000000000}{2:I940CLIENTBICXXXXN}{4:\n:20:TESTREF\n:25:123456789\n:28C:00001/001\n:60F:C210301EUR2000,\n:62F:C210301EUR1850,\n-}"}'
```

## 6) Scenario Checklist

Run these in order:

1. Single-page happy path
2. Multi-page aggregation complete path
3. Multi-page timeout (`AggregationExpiryScheduler`)
4. No routing match -> `EXCEPTION.QUEUE`
5. Routing branch exclusion by specific `messageType`
6. Routing branch exclusion wildcard (`null/blank/*`)
7. Delivery failure behavior (message should not be marked completed if delivery fails)

## 7) Where Configuration Lives

- App defaults: `src/main/resources/application.yml`
- DB schema/data: `src/main/resources/db/migration/*.sql`
- Routing file load source: `src/main/resources/rules/routing-rules.csv`

## 8) Security and Access

- Public for testing: `/test/**`, `/h2-console/**`, `/actuator/**`
- Protected APIs: `/api/**` (basic auth, default `admin/admin123`)
- Security config: `src/main/java/com/bank/mt/config/SecurityConfig.java`

## 9) Common Troubleshooting

1. App fails to start on 8080: free the port and restart.
2. No delivery observed: check `/test/deliveries`, routing rules, exclusions, and relay config.
3. Message stuck `PROCESSING`: inspect aggregation status and timeout scheduler behavior.
4. Unexpected routing skip: verify branch exclusion + `messageType` match.

## 10) Suggested Ramp-Up Plan

Day 1:

- Read sections 1-4
- Run app and execute checklist items 1-2

Day 2:

- Execute checklist items 3-7
- Make one small change and verify through dashboard + test endpoint
