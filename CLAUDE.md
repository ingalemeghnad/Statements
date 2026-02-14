# MT Statement Processor - Claude Context

## Project
Spring Boot 3.2.2 / Java 17 (runs on Java 23) POC for processing SWIFT MT940/941/942/950 statements.

## Architecture
```
MQ Inbound → MqIngestionStrategy → ODS (audit) → Parser → Aggregation
                                                              ↓ complete
                                                       Statement Routing
                                                         ↓ rule match
                                                       DeliveryService
                                              ↓ downstream queues    ↓ SWIFT relay (BIC replaced)
                                        RECON/GL/CASH/ARCHIVE    SWIFT.ALLIANCE.OUTBOUND
```

## Key Source Paths
- **Ingestion**: `src/main/java/com/bank/mt/ingestion/MqIngestionStrategy.java` — entry point, called directly by OdsController for POC (production would use @JmsListener)
- **Parser**: `src/main/java/com/bank/mt/parsing/MtParser.java` — extracts BICs (normalized to 8-char), account, msg type, page info from balance tags (:60F:/:60M:/:62F:/:62M:)
- **Aggregation**: `src/main/java/com/bank/mt/aggregation/AggregationService.java` — multi-page collection, SHA-256 duplicate detection, combined SWIFT FIN output (single header + merged transactions)
- **Routing**: `src/main/java/com/bank/mt/routing/RoutingService.java` — cached rules + relay config, wildcard matching
- **Delivery**: `src/main/java/com/bank/mt/delivery/DeliveryService.java` — downstream delivery + SWIFT relay with Block 2 receiver BIC replacement
- **Domain**: `src/main/java/com/bank/mt/domain/` — MtStatement, DeliveryInstruction, RelayConfig (has swiftReceiverBic), RoutingRule, etc.
- **Migrations**: `src/main/resources/db/migration/V1-V9` — schema + sample data
- **CSV rules**: `src/main/resources/rules/routing-rules.csv`
- **UI**: `src/main/resources/static/index.html` (dashboard), `samples.html` (test scenarios)
- **Tests**: `src/test/java/com/bank/mt/EndToEndIntegrationTest.java` — 5 e2e tests; `AggregationTimeoutTest`, `MtParserTest`

## BIC Setup
| Use | Sender | Receiver | Account |
|---|---|---|---|
| MT940/950 routing | HSBCGB2L | CITIUS33 | 123456789 |
| MT942 routing + relay | DEUTDEFF | BNPAFRPP | 987654321 |
| Relay SWIFT BIC replacement | — | COBADEFF (replaces BNPAFRPP in Block 2) | — |
| MT941 wildcard | COBADEFF | SOGEFRPP | 555555555 |

## Queue Names
- `RECON.INTELLIMATCH.IN` — MT940 reconciliation
- `GL.SAP.STMT.FEED` — MT950 general ledger
- `CASH.CALYPSO.INTRADAY` — MT942 cash management
- `ARCHIVE.COMPLI.STORE` — MT941 compliance archive
- `SWIFT.ALLIANCE.OUTBOUND` — SWIFT relay (receiver BIC replaced per relay config)

## Key Constraints
- **H2 file-based DB**: `jdbc:h2:file:./data/mtdb` with `clean-on-validation-error: true`. Delete `data/` dir if migrations change.
- **Tests use in-memory H2**: All test classes must have `@ActiveProfiles("test")` to use `jdbc:h2:mem:testdb`
- **No embedded MQ broker**: Artemis incompatible with Java 23 (`Subject.getSubject()` removed). POC calls `MqIngestionStrategy.onMessage()` directly.
- **8-char BICs**: Parser normalizes BICs to 8 chars. DB routing_rule/relay_config must store 8-char BICs.
- **Flyway migrations**: V1 (schema), V2 (sample data), V3-V5 (filters/pages — some tables later dropped), V6 (aggregation txn ref), V7-V8 (drop removed tables), V9 (relay swift_receiver_bic)
- **Git branch**: `mq-ingestion-cleanup` on `origin` (GitHub: ingalemeghnad/Statements)

## Build & Test
```bash
./mvnw clean test        # 12 tests
./mvnw spring-boot:run   # http://localhost:8080
```
