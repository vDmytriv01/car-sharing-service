--liquibase formatted sql

--changeset vdmytriv:07-create-payments-table
CREATE TABLE payments
(
    id            BIGINT AUTO_INCREMENT NOT NULL,
    status        VARCHAR(20) NOT NULL,
    type          VARCHAR(20) NOT NULL,
    rental_id     BIGINT NOT NULL,
    session_url   VARCHAR(2048) NOT NULL,
    session_id    VARCHAR(255) NOT NULL,
    amount_to_pay DECIMAL(10, 2) NOT NULL,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT uk_payments_session_id UNIQUE (session_id),
    CONSTRAINT fk_payments_rental FOREIGN KEY (rental_id) REFERENCES rentals (id),
    CONSTRAINT chk_payments_status CHECK (status IN ('PENDING', 'PAID')),
    CONSTRAINT chk_payments_type CHECK (type IN ('PAYMENT', 'FINE')),
    CONSTRAINT chk_payments_amount_to_pay CHECK (amount_to_pay > 0)
);

CREATE INDEX idx_payments_rental_id_status ON payments (rental_id, status);
