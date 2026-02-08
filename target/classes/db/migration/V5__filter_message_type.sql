-- Add optional message_type to filter tables (NULL = applies to all types)
ALTER TABLE aggregation_bic_filter ADD COLUMN message_type VARCHAR(10);
ALTER TABLE routing_bic_exclusion ADD COLUMN message_type VARCHAR(10);
