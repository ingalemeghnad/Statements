ALTER TABLE relay_config ADD COLUMN swift_receiver_bic VARCHAR(20);

-- Update sample relay config: SWIFT copies for Deutsche Bank → BNP Paribas go to Commerzbank
UPDATE relay_config SET swift_receiver_bic = 'COBADEFF' WHERE account_number = '987654321';
