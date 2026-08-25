-- User identity hardening.
--
-- 1. Profile columns.
--
--    Deliberately nullable. Rows created before this migration have no real name or phone
--    to backfill, and writing '' or 'Unknown' would permanently erase the difference
--    between "never collected" and "left blank". The write path enforces presence instead
--    (@NotBlank on RegisterRequest / UpdateProfileRequest), so every new or updated record
--    carries them. A later migration can add NOT NULL once legacy rows have been filled in.
ALTER TABLE users
    ADD COLUMN first_name varchar(100),
    ADD COLUMN last_name  varchar(100),
    ADD COLUMN phone      varchar(30);

-- 2. Repair is_enabled.
--
--    User.isEnabled initialises to true, but the entity is @Builder without @Builder.Default,
--    so Lombok dropped the initializer and AuthService.register wrote false for every account
--    ever created. No code path has ever disabled a user deliberately, so every false in this
--    table is that bug. The entity fix ships in the same commit.
UPDATE users SET is_enabled = true WHERE is_enabled = false;
