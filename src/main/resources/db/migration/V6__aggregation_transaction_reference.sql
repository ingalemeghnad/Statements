-- Strengthen aggregation grouping key to avoid mixing statements that share
-- statement_number/account_number/message_type but have different references.
ALTER TABLE mt_aggregation ADD COLUMN transaction_reference VARCHAR(100) NOT NULL DEFAULT '';

DROP INDEX idx_agg_stmt_acct;
CREATE UNIQUE INDEX idx_agg_stmt_acct_ref
    ON mt_aggregation(statement_number, account_number, message_type, transaction_reference);
