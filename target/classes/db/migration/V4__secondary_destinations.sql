-- Add secondary destinations to routing rules (comma-separated, up to 10)
ALTER TABLE routing_rule ADD COLUMN secondary_destinations VARCHAR(1000);
