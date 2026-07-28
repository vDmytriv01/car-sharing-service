--liquibase formatted sql

--changeset vdmytriv:06-create-rentals-table
CREATE TABLE rentals
(
    id                 BIGINT AUTO_INCREMENT NOT NULL,
    rental_date        DATE NOT NULL,
    return_date        DATE NOT NULL,
    actual_return_date DATE NULL,
    car_id             BIGINT NOT NULL,
    user_id            BIGINT NOT NULL,
    CONSTRAINT pk_rentals PRIMARY KEY (id),
    CONSTRAINT fk_rentals_car FOREIGN KEY (car_id) REFERENCES cars (id),
    CONSTRAINT fk_rentals_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_rentals_return_date CHECK (return_date >= rental_date),
    CONSTRAINT chk_rentals_actual_return_date
        CHECK (actual_return_date IS NULL OR actual_return_date >= rental_date)
);

CREATE INDEX idx_rentals_car_id ON rentals (car_id);
CREATE INDEX idx_rentals_actual_return_date_return_date
    ON rentals (actual_return_date, return_date);
CREATE INDEX idx_rentals_user_actual_return_date ON rentals (user_id, actual_return_date);
