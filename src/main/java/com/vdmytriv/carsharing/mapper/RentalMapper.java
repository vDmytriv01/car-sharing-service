package com.vdmytriv.carsharing.mapper;

import com.vdmytriv.carsharing.dto.rental.RentalResponse;
import com.vdmytriv.carsharing.model.Rental;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RentalMapper {

    private final CarMapper carMapper;

    public RentalResponse toResponse(Rental rental) {
        return new RentalResponse(
                rental.getId(),
                rental.getRentalDate(),
                rental.getReturnDate(),
                rental.getActualReturnDate(),
                carMapper.toResponse(rental.getCar()),
                rental.getUser().getId()
        );
    }
}
