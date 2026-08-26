-- Image columns now hold an object-storage key (e.g. business/<id>/logo/<uuid>.webp),
-- not a URL. The public URL is built at response time from the configured bucket, so
-- the provider hostname is no longer baked into every row.
--
-- No backfill: rows written before this change hold a full URL, and the URL resolver
-- passes any value already starting with http through unchanged. Those images keep
-- rendering until they are next replaced, at which point they become keys.

ALTER TABLE businesses RENAME COLUMN logo_url TO logo_key;
ALTER TABLE businesses RENAME COLUMN cover_image_url TO cover_image_key;
ALTER TABLE employees RENAME COLUMN photo_url TO photo_key;
