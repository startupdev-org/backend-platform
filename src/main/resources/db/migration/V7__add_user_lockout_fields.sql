-- Per-account login lockout. failed_login_attempts counts consecutive failures since the
-- last successful login; locked_until, when set and in the future, blocks login entirely.
-- Both are managed by AuthService.login. Existing rows start unlocked with a zero counter.

ALTER TABLE users
    ADD COLUMN failed_login_attempts integer NOT NULL DEFAULT 0,
    ADD COLUMN locked_until timestamp(6);
