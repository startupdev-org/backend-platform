-- Baseline schema, generated from the JPA entity mappings.
--
-- Databases that already carry a schema created by the old `ddl-auto: update`
-- are baselined past this file (spring.flyway.baseline-on-migrate=true); it runs
-- in full only against an empty database. From here on, every entity change
-- ships with its own V<n>__*.sql — `ddl-auto: validate` will fail startup if the
-- entities and the migrations ever drift apart.

CREATE TABLE users (
    id          uuid         NOT NULL,
    email       varchar(255) NOT NULL UNIQUE,
    password    varchar(255) NOT NULL,
    role        varchar(255) NOT NULL CHECK (role IN ('PLATFORM_ADMIN', 'BUSINESS_ADMIN')),
    is_enabled  boolean      NOT NULL DEFAULT true,
    created_at  timestamp(6) NOT NULL,
    updated_at  timestamp(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE businesses (
    id                    uuid         NOT NULL,
    owner_id              uuid         NOT NULL,
    name                  varchar(255) NOT NULL,
    slug                  varchar(255) NOT NULL UNIQUE,
    description           TEXT,
    address               varchar(255) NOT NULL,
    city                  varchar(255) NOT NULL,
    phone                 varchar(255) NOT NULL,
    business_email        varchar(255),
    website               varchar(255),
    logo_url              varchar(255),
    cover_image_url       varchar(255),
    business_category     varchar(255) CHECK (business_category IN ('BARBERSHOP', 'BEAUTY', 'SPA', 'NAILS')),
    service_delivery_type varchar(255) CHECK (service_delivery_type IN ('ON_SITE', 'MOBILE', 'HYBRID')),
    rating_overall        NUMERIC(3, 2) DEFAULT 0,
    created_at            timestamp(6) NOT NULL,
    updated_at            timestamp(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE business_features (
    feature_id  bigserial    NOT NULL,
    business_id uuid         NOT NULL,
    name        varchar(255) NOT NULL UNIQUE,
    PRIMARY KEY (feature_id),
    UNIQUE (business_id, name)
);

CREATE TABLE business_working_hours (
    id          bigserial   NOT NULL,
    business_id uuid        NOT NULL,
    day_of_week varchar(20) NOT NULL CHECK (day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    open_time   time(6)     NOT NULL,
    close_time  time(6)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_business_day_time UNIQUE (business_id, day_of_week, open_time, close_time)
);

CREATE TABLE locations (
    id                  uuid    NOT NULL,
    business_id         uuid,
    name                varchar(255),
    address             varchar(255),
    city                varchar(255),
    country             varchar(255),
    latitude            float(53),
    longitude           float(53),
    is_default_location boolean NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE employees (
    id          uuid         NOT NULL,
    business_id uuid         NOT NULL,
    name        varchar(255) NOT NULL,
    photo_url   varchar(255),
    active      boolean      NOT NULL,
    created_at  timestamp(6) NOT NULL,
    updated_at  timestamp(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE services (
    id               uuid          NOT NULL,
    business_id      uuid          NOT NULL,
    name             varchar(255)  NOT NULL,
    description      TEXT,
    price            numeric(38, 2) NOT NULL,
    duration_minutes integer       NOT NULL,
    active           boolean       NOT NULL,
    created_at       timestamp(6)  NOT NULL,
    updated_at       timestamp(6)  NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE employee_location_service_price (
    id          uuid           NOT NULL,
    employee_id uuid           NOT NULL,
    service_id  uuid           NOT NULL,
    location_id uuid           NOT NULL,
    price       numeric(38, 2) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (employee_id, service_id, location_id)
);

CREATE TABLE bookings (
    id                                 uuid         NOT NULL,
    employee_id                        uuid         NOT NULL,
    provided_service_id                uuid         NOT NULL,
    employee_location_service_price_id uuid         NOT NULL,
    location_id                        uuid,
    customer_name                      varchar(255) NOT NULL,
    customer_email                     varchar(255) NOT NULL,
    customer_phone                     varchar(255) NOT NULL,
    status                             varchar(255) NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED')),
    start_time                         timestamp(6) NOT NULL,
    end_time                           timestamp(6) NOT NULL,
    street                             varchar(255),
    city                               varchar(255),
    postal_code                        varchar(255),
    country                            varchar(255),
    latitude                           float(53),
    longitude                          float(53),
    created_at                         timestamp(6) NOT NULL,
    updated_at                         timestamp(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE reviews (
    id                 uuid          NOT NULL,
    booking_id         uuid          NOT NULL UNIQUE,
    rating_overall     NUMERIC(3, 2) NOT NULL,
    rating_service     NUMERIC(3, 2),
    rating_cleanliness NUMERIC(3, 2),
    rating_price       NUMERIC(3, 2),
    comment            TEXT,
    business_reply     TEXT,
    created_at         timestamp(6)  NOT NULL,
    updated_at         timestamp(6)  NOT NULL,
    PRIMARY KEY (id)
);

ALTER TABLE businesses
    ADD CONSTRAINT fk_businesses_owner FOREIGN KEY (owner_id) REFERENCES users;

ALTER TABLE business_features
    ADD CONSTRAINT fk_business_features_business FOREIGN KEY (business_id) REFERENCES businesses;

ALTER TABLE business_working_hours
    ADD CONSTRAINT fk_business_working_hours_business FOREIGN KEY (business_id) REFERENCES businesses;

ALTER TABLE locations
    ADD CONSTRAINT fk_locations_business FOREIGN KEY (business_id) REFERENCES businesses;

ALTER TABLE employees
    ADD CONSTRAINT fk_employees_business FOREIGN KEY (business_id) REFERENCES businesses;

ALTER TABLE services
    ADD CONSTRAINT fk_services_business FOREIGN KEY (business_id) REFERENCES businesses;

ALTER TABLE employee_location_service_price
    ADD CONSTRAINT fk_elsp_employee FOREIGN KEY (employee_id) REFERENCES employees;

ALTER TABLE employee_location_service_price
    ADD CONSTRAINT fk_elsp_location FOREIGN KEY (location_id) REFERENCES locations;

ALTER TABLE employee_location_service_price
    ADD CONSTRAINT fk_elsp_service FOREIGN KEY (service_id) REFERENCES services;

ALTER TABLE bookings
    ADD CONSTRAINT fk_bookings_employee FOREIGN KEY (employee_id) REFERENCES employees;

ALTER TABLE bookings
    ADD CONSTRAINT fk_bookings_location FOREIGN KEY (location_id) REFERENCES locations;

ALTER TABLE bookings
    ADD CONSTRAINT fk_bookings_elsp FOREIGN KEY (employee_location_service_price_id) REFERENCES employee_location_service_price;

ALTER TABLE bookings
    ADD CONSTRAINT fk_bookings_service FOREIGN KEY (provided_service_id) REFERENCES services;

ALTER TABLE reviews
    ADD CONSTRAINT fk_reviews_booking FOREIGN KEY (booking_id) REFERENCES bookings;
