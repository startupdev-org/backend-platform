-- Indexes on the foreign-key columns of the core business tables, plus the two
-- indexes the booking-conflict check needs. Before this file the only indexes
-- outside refresh_tokens / password_reset_tokens were the ones Postgres creates
-- implicitly for PRIMARY KEY and UNIQUE, so every join and every owner/business
-- scoped lookup was a sequential scan. That is invisible at today's row counts
-- and becomes the dominant cost once bookings accumulate. See BP-51.
--
-- Two rules were applied when picking this set:
--
--   1. An index has to be earned by a real query in src/main/java/com/platform/
--      repository, or by the referential-integrity check Postgres runs on the
--      *parent* row when it is deleted (that check scans the child's FK column
--      and is the reason an FK nobody selects on still wants an index).
--   2. A composite UNIQUE constraint already indexes its leftmost prefix, so a
--      separate single-column index on that prefix would only cost writes. The
--      columns skipped for that reason are listed at the bottom of this file.
--
-- Plain CREATE INDEX, not CONCURRENTLY: both databases are pre-launch and tiny,
-- and CONCURRENTLY cannot run inside the transaction Flyway wraps each migration
-- in. IF NOT EXISTS so the migration is a no-op against any index already added
-- by hand.

-- businesses.owner_id
--   BusinessRepository.findByOwnerId / findByOwnerIdOrderByCreatedAtAsc /
--   existsByOwnerId. Every ownership check and every /whoami hits this.
CREATE INDEX IF NOT EXISTS idx_businesses_owner_id ON businesses (owner_id);

-- employees.business_id
--   EmployeeRepository.findByBusinessIdAndEnabled / findByBusinessIdInAndEnabled.
--   Left single-column on purpose: `enabled` is a two-value column and a business
--   has few employees, so it is cheaper as a filter on the rows this index already
--   narrowed than as a second index column. It is also the entry point Postgres
--   uses for the business -> employee -> price -> booking chain in
--   ReviewRepository.findByBusinessId / countByBusinessId / getAverageRatingByBusiness
--   and BookingRepository.findByBusinessAndStatus / countCompletedByBusiness.
CREATE INDEX IF NOT EXISTS idx_employees_business_id ON employees (business_id);

-- services.business_id
--   ServiceRepository.findByBusinessId / findByBusinessIdIn / findByBusinessIdAndActive.
CREATE INDEX IF NOT EXISTS idx_services_business_id ON services (business_id);

-- locations.business_id
--   LocationRepository.findByBusinessId / findByBusinessIdIn /
--   findByBusinessIdAndIsDefaultLocationTrue.
CREATE INDEX IF NOT EXISTS idx_locations_business_id ON locations (business_id);

-- employee_location_service_price.service_id and .location_id
--   EmployeeLocationServicePriceRepository.findByServiceId / findByLocationId.
--   Neither is the leftmost column of UNIQUE (employee_id, service_id, location_id),
--   so that constraint's index cannot serve them. `elsp` matches the abbreviation
--   V1 already uses for this table's constraint names (fk_elsp_service, ...).
CREATE INDEX IF NOT EXISTS idx_elsp_service_id  ON employee_location_service_price (service_id);
CREATE INDEX IF NOT EXISTS idx_elsp_location_id ON employee_location_service_price (location_id);

-- bookings (employee_location_service_price_id, start_time)
--   The booking-conflict path. BookingRepository.findByEmployeeAndDateRange
--   resolves the employee through the price entry, so the plan is:
--     employee_location_service_price via UNIQUE (employee_id, ...)  ->  a handful
--     of price-entry ids  ->  nested loop into bookings with this index, equality
--     on employee_location_service_price_id and a range on start_time both applied
--     as the index condition rather than as a post-scan filter.
--   Column order matters: equality column first, range column second.
--   This shape survives BP-31 (the overlap predicate is missing its end_time half).
--   Once it becomes `start_time < :end AND end_time > :start`, the equality plus the
--   start_time upper bound still drive the index and end_time stays a cheap filter.
--   Being led by employee_location_service_price_id, it also covers that FK for
--   the parent-delete check, so no separate single-column index is needed.
CREATE INDEX IF NOT EXISTS idx_bookings_elsp_start_time
    ON bookings (employee_location_service_price_id, start_time);

-- bookings.start_time
--   The same query from the other end. The composite above is led by the price
--   entry, so it cannot drive a plan that starts from the date range - and the
--   ranges here are narrow: one service duration for the conflict check
--   (BookingService.createBooking) and a single day for AvailabilityService.
--   This gives the planner the start_time-first path for when the date window is
--   more selective than the employee's history, which is the common case for an
--   employee with a long booking history.
CREATE INDEX IF NOT EXISTS idx_bookings_start_time ON bookings (start_time);

-- Deliberately NOT indexed, and why:
--
--   business_working_hours.business_id  - leftmost column of
--       uk_business_day_time UNIQUE (business_id, day_of_week, open_time, close_time),
--       which therefore already serves findByBusinessId, findByBusinessIdIn and
--       findByBusinessIdAndDayOfWeek as well as the parent-delete check. BP-51 lists
--       this column as unindexed; it is not.
--
--   business_features.business_id       - leftmost column of UNIQUE (business_id, name),
--       which already serves findByBusinessId, findByBusinessIdIn and
--       existsByBusinessIdAndName.
--
--   employee_location_service_price.employee_id - leftmost column of
--       UNIQUE (employee_id, service_id, location_id), which already serves
--       findByEmployeeId, findByEmployeeIdAndLocationId and
--       findByEmployeeIdAndServiceIdAndLocationId.
--
--   reviews.booking_id                  - NOT NULL UNIQUE, so already indexed.
--
--   bookings.status                     - BookingRepository.findByStatus and the
--       *ForListing variants filter on it, but it holds three values over the whole
--       table; a scan beats an index for any status that is not rare. Where it is
--       paired with a business or employee, those indexes carry the query and status
--       is the filter. Revisit with real cardinality, not now.
--
--   businesses.city / rating_overall / business_category - the
--       BusinessSpecifications filters. city is a case-insensitive `LIKE '%x%'`,
--       which a plain btree cannot serve at all; the other two need real selectivity
--       data to size. Out of scope for BP-51, which is FK columns and start_time.
--
-- ON DELETE was reviewed here and deliberately left alone: no FK in this schema
-- specifies any action, so deletes work only through the Java-side cascades and a
-- direct SQL DELETE errors. Changing that is a behaviour change, not an index, and
-- belongs in its own ticket.
