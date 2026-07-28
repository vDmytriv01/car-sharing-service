package com.vdmytriv.carsharing.dto.rental;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record RentalCreateRequest(
        @NotNull
        @FutureOrPresent
        LocalDate returnDate,

        @NotNull
        @Positive
        Long carId
) {
}
