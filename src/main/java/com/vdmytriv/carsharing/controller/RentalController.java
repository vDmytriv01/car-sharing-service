package com.vdmytriv.carsharing.controller;

import com.vdmytriv.carsharing.dto.PageResponse;
import com.vdmytriv.carsharing.dto.rental.RentalCreateRequest;
import com.vdmytriv.carsharing.dto.rental.RentalResponse;
import com.vdmytriv.carsharing.service.RentalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Rentals", description = "Car rental management")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/rentals")
@RequiredArgsConstructor
public class RentalController {

    private final RentalService rentalService;

    @Operation(summary = "Create a rental")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RentalResponse create(
            Principal principal,
            @RequestBody @Valid RentalCreateRequest request
    ) {
        return rentalService.create(principal.getName(), request);
    }

    @Operation(summary = "Get rentals with optional filters")
    @GetMapping
    public PageResponse<RentalResponse> findAll(
            Principal principal,
            @RequestParam(name = "user_id", required = false) Long userId,
            @RequestParam(name = "is_active", required = false) Boolean active,
            @PageableDefault(size = 20, sort = "id") Pageable pageable
    ) {
        return rentalService.findAll(
                principal.getName(),
                userId,
                active,
                pageable
        );
    }

    @Operation(summary = "Get a rental by id")
    @GetMapping("/{id}")
    public RentalResponse findById(
            Principal principal,
            @PathVariable Long id
    ) {
        return rentalService.findById(principal.getName(), id);
    }

    @Operation(summary = "Return a rented car")
    @PostMapping("/{id}/return")
    public RentalResponse returnRental(
            Principal principal,
            @PathVariable Long id
    ) {
        return rentalService.returnRental(principal.getName(), id);
    }
}
