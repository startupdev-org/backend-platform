-- Single-use password reset tokens. Before this table a forgotten password meant a
-- permanently lost account: there was no self-service route and, deliberately, no
-- admin-sets-your-password route either (that is an account-takeover primitive).
--
-- Only the SHA-256 of the token is stored, exactly as for refresh_tokens - a dump of
-- this table hands nobody a working reset link. A row is single-use: used_at is stamped
-- when the token is spent, and also when it is superseded by a newer request or by the
-- user changing their password through the authenticated route.

CREATE TABLE password_reset_tokens (
    id         uuid         PRIMARY KEY,
    user_id    uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash varchar(64)  NOT NULL UNIQUE,
    issued_at  timestamp(6) NOT NULL,
    expires_at timestamp(6) NOT NULL,
    used_at    timestamp(6)
);

CREATE INDEX idx_password_reset_tokens_user_id    ON password_reset_tokens (user_id);
CREATE INDEX idx_password_reset_tokens_expires_at ON password_reset_tokens (expires_at);
