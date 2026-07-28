package com.vdmytriv.carsharing.dto.rental;

import com.vdmytriv.carsharing.dto.car.CarResponse;
import java.time.LocalDate;

public record RentalResponse(
        Long id,
        LocalDate rentalDate,
        LocalDate returnDate,
        LocalDate actualReturnDate,
        CarResponse car,
        Long userId
) {
}
