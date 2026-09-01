-- BusinessFeature.name must be unique per business, not platform-wide.
--
-- V1 declared the correct composite constraint - UNIQUE (business_id, name) - and, on the
-- same column definition, a redundant column-level UNIQUE on name alone:
--   name varchar(255) NOT NULL UNIQUE
-- Postgres backs a column UNIQUE constraint with an auto-generated name (something like
-- business_features_name_key), so the first business to create a feature called "WiFi"
-- permanently claimed that name for every business on the platform. It also failed badly:
-- FeatureService.addFeature only pre-checks existsByBusinessIdAndName, so the collision
-- surfaced as a raw DataIntegrityViolationException (generic 409) instead of the
-- purpose-built BusinessFeatureAlreadyExistsException.
--
-- This migration drops only the column-level constraint, found by shape - a UNIQUE
-- constraint on business_features whose column list is exactly {name} - rather than by
-- an assumed name, since Postgres's auto-generated name was never verified against a real
-- database from this environment. The composite (business_id, name) constraint is left
-- untouched throughout.
DO $$
DECLARE
    single_col_constraint_name text;
BEGIN
    SELECT con.conname
      INTO single_col_constraint_name
      FROM pg_constraint con
      JOIN pg_class rel ON rel.oid = con.conrelid
      JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
     WHERE con.contype = 'u'
       AND rel.relname = 'business_features'
       AND nsp.nspname = current_schema()
       AND (
           SELECT array_agg(att.attname ORDER BY att.attname)
             FROM unnest(con.conkey) AS colnum
             JOIN pg_attribute att
               ON att.attrelid = con.conrelid AND att.attnum = colnum
       ) = ARRAY['name'];

    IF single_col_constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE business_features DROP CONSTRAINT %I', single_col_constraint_name);
    END IF;
END $$;

-- Belt-and-braces: confirm the constraint that actually enforces per-business uniqueness
-- is present, and create it if it somehow is not. V1__baseline_schema.sql:46 already
-- creates `UNIQUE (business_id, name)` as part of the CREATE TABLE statement, so this is
-- expected to be a no-op everywhere - but this migration is written with no access to a
-- real database to confirm that, so it is guarded rather than assumed.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint con
          JOIN pg_class rel ON rel.oid = con.conrelid
          JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
         WHERE con.contype = 'u'
           AND rel.relname = 'business_features'
           AND nsp.nspname = current_schema()
           AND (
               SELECT array_agg(att.attname ORDER BY att.attname)
                 FROM unnest(con.conkey) AS colnum
                 JOIN pg_attribute att
                   ON att.attrelid = con.conrelid AND att.attnum = colnum
           ) = ARRAY['business_id', 'name']
    ) THEN
        ALTER TABLE business_features
            ADD CONSTRAINT business_features_business_id_name_key UNIQUE (business_id, name);
    END IF;
END $$;
