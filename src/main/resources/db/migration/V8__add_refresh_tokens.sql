-- Server-side refresh tokens. Access tokens are short-lived and unrevocable by design;
-- revocation lives here instead. Only the SHA-256 of the token is stored, so a dump of
-- this table does not yield usable credentials.
--
-- A row is single-use: refreshing revokes it and inserts its successor, recording the
-- successor's hash in replaced_by. Presenting an already-revoked token is treated as a
-- leak and revokes the whole family (see RefreshTokenService).

CREATE TABLE refresh_tokens (
    id          uuid         PRIMARY KEY,
    user_id     uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  varchar(64)  NOT NULL UNIQUE,
    issued_at   timestamp(6) NOT NULL,
    expires_at  timestamp(6) NOT NULL,
    revoked_at  timestamp(6),
    replaced_by varchar(64)
);

CREATE INDEX idx_refresh_tokens_user_id    ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
