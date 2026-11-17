ALTER TABLE account_email_deliveries ADD COLUMN provider VARCHAR(16);
ALTER TABLE account_email_deliveries ADD COLUMN provider_message_id VARCHAR(40);
ALTER TABLE account_email_deliveries ADD COLUMN failure_category VARCHAR(32);
