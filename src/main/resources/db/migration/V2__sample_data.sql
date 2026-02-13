-- ============================================================
-- Sample ODS messages for local testing
-- ============================================================

-- 1. Single-page MT940 (HSBC UK → Citi US)
INSERT INTO mt_message_ods (raw_message, status) VALUES (
'{1:F01HSBCGB2LAXXX0000000000}{2:I940CITIUS33XXXXN}{4:
:20:REF123
:25:123456789
:28C:00001/001
:60F:C210101EUR1000,
:61:2101010101DR100,NTRFREF001//ACCT-OWNER
:62F:C210101EUR900,
-}',
'NEW');

-- 2. Multi-page MT940 — page 1 of 2 (HSBC UK → Citi US)
INSERT INTO mt_message_ods (raw_message, status) VALUES (
'{1:F01HSBCGB2LAXXX0000000000}{2:I940CITIUS33XXXXN}{4:
:20:REF456
:25:123456789
:28C:00002/002
:60F:C210201EUR5000,
:61:2102010201DR200,NTRFREF002//ACCT-OWNER
:61:2102010201DR300,NTRFREF003//ACCT-OWNER
-}',
'NEW');

-- 3. Multi-page MT940 — page 2 of 2 (HSBC UK → Citi US)
INSERT INTO mt_message_ods (raw_message, status) VALUES (
'{1:F01HSBCGB2LAXXX0000000000}{2:I940CITIUS33XXXXN}{4:
:20:REF456
:25:123456789
:28C:00002/001
:60M:C210201EUR4500,
:61:2102010201CR1000,NTRFREF004//ACCT-OWNER
:62F:C210201EUR5500,
-}',
'NEW');

-- 4. MT942 interim transaction report (Deutsche Bank → BNP Paribas)
INSERT INTO mt_message_ods (raw_message, status) VALUES (
'{1:F01DEUTDEFFAXXX0000000000}{2:I942BNPAFRPPXXXXN}{4:
:20:REF789
:25:987654321
:28C:00001/001
:34F:EUR100,
:13D:2101011200+0100
:61:2101010101CR500,NTRFREF005//ACCT-OWNER
:90D:1EUR100,
:90C:1EUR500,
-}',
'NEW');

-- ============================================================
-- Sample routing rule (HSBC UK → Citi US for MT940)
-- ============================================================
INSERT INTO routing_rule (account_number, message_type, sender_bic, receiver_bic,
                          destination_queue, active, source)
VALUES ('123456789', 'MT940', 'HSBCGB2L', 'CITIUS33', 'REPORTING.Q1', true, 'UI');

-- ============================================================
-- Sample relay config (Deutsche Bank → BNP Paribas)
-- ============================================================
INSERT INTO relay_config (account_number, sender_bic, receiver_bic, active)
VALUES ('987654321', 'DEUTDEFF', 'BNPAFRPP', true);
