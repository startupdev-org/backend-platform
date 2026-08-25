-- Remove redundant direct foreign keys from bookings table
-- Booking now uses only employee_location_service_price_id to access employee, service, and location

ALTER TABLE bookings
    DROP CONSTRAINT fk_bookings_employee;

ALTER TABLE bookings
    DROP CONSTRAINT fk_bookings_service;

ALTER TABLE bookings
    DROP CONSTRAINT fk_bookings_location;

ALTER TABLE bookings
    DROP COLUMN employee_id;

ALTER TABLE bookings
    DROP COLUMN provided_service_id;

ALTER TABLE bookings
    DROP COLUMN location_id;
