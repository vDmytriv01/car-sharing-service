--liquibase formatted sql

--changeset vdmytriv:08-add-payment-rental-type-constraint
ALTER TABLE payments
    ADD CONSTRAINT uk_payments_rental_type UNIQUE (rental_id, type);
