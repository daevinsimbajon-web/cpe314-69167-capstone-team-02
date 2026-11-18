-- Existing accounts remain usable; no historical email is falsely marked verified.
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN verification_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMP WITH TIME ZONE;
CREATE TABLE account_email_challenges (
 id UUID PRIMARY KEY, user_id UUID REFERENCES users(id), ticket_digest VARCHAR(64) NOT NULL UNIQUE,
 purpose VARCHAR(24) NOT NULL CHECK(purpose IN ('EMAIL_VERIFICATION','PASSWORD_RESET')),
 email_address VARCHAR(254) NOT NULL, code_digest VARCHAR(64) NOT NULL,
 expires_at TIMESTAMP WITH TIME ZONE NOT NULL, resend_at TIMESTAMP WITH TIME ZONE NOT NULL,
 attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 5),
 consumed_at TIMESTAMP WITH TIME ZONE, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 delivery_status VARCHAR(12) NOT NULL DEFAULT 'QUEUED'
);
CREATE INDEX ix_email_challenges_user ON account_email_challenges(user_id,purpose,created_at);
CREATE TABLE password_reset_grants (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id), grant_digest VARCHAR(64) NOT NULL UNIQUE,
 purpose VARCHAR(24) NOT NULL DEFAULT 'PASSWORD_RESET' CHECK(purpose='PASSWORD_RESET'),
 expires_at TIMESTAMP WITH TIME ZONE NOT NULL, consumed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX ix_reset_grants_user ON password_reset_grants(user_id);
CREATE TABLE account_email_deliveries (
 id UUID PRIMARY KEY, user_id UUID REFERENCES users(id), challenge_id UUID REFERENCES account_email_challenges(id),
 kind VARCHAR(24) NOT NULL, status VARCHAR(12) NOT NULL DEFAULT 'QUEUED',
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
