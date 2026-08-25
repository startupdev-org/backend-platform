-- Add new columns to employees table
ALTER TABLE employees
    ADD COLUMN first_name VARCHAR(255) NOT NULL DEFAULT '',
    ADD COLUMN last_name VARCHAR(255) NOT NULL DEFAULT '',
    ADD COLUMN email VARCHAR(255),
    ADD COLUMN phone_number VARCHAR(255);

-- Drop the old name column
ALTER TABLE employees DROP COLUMN name;

-- Remove the default values from the new columns now that they're populated
ALTER TABLE employees
    ALTER COLUMN first_name DROP DEFAULT,
    ALTER COLUMN last_name DROP DEFAULT;
