--liquibase formatted sql

--changeset vdmytriv:05-create-cars-table
CREATE TABLE cars
(
    id         BIGINT AUTO_INCREMENT NOT NULL,
    model      VARCHAR(100) NOT NULL,
    brand      VARCHAR(100) NOT NULL,
    type       VARCHAR(20) NOT NULL,
    inventory  INT NOT NULL,
    daily_fee  DECIMAL(10, 2) NOT NULL,
    is_deleted BOOLEAN DEFAULT FALSE NOT NULL,
    CONSTRAINT pk_cars PRIMARY KEY (id),
    CONSTRAINT chk_cars_type
        CHECK (type IN ('SEDAN', 'SUV', 'HATCHBACK', 'UNIVERSAL')),
    CONSTRAINT chk_cars_inventory CHECK (inventory >= 0),
    CONSTRAINT chk_cars_daily_fee CHECK (daily_fee > 0)
);
