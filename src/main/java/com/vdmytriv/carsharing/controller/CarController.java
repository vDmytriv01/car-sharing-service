package com.vdmytriv.carsharing.controller;

import com.vdmytriv.carsharing.dto.PageResponse;
import com.vdmytriv.carsharing.dto.car.CarCreateRequest;
import com.vdmytriv.carsharing.dto.car.CarPatchRequest;
import com.vdmytriv.carsharing.dto.car.CarResponse;
import com.vdmytriv.carsharing.dto.car.CarSearchCriteria;
import com.vdmytriv.carsharing.dto.car.CarUpdateRequest;
import com.vdmytriv.carsharing.service.CarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cars", description = "Car catalogue and fleet management")
@RestController
@RequestMapping("/cars")
@RequiredArgsConstructor
public class CarController {

    private final CarService carService;

    @Operation(summary = "Add a car to the fleet")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CarResponse create(@RequestBody @Valid CarCreateRequest request) {
        return carService.create(request);
    }

    @Operation(summary = "Get cars with optional filters")
    @GetMapping
    public PageResponse<CarResponse> findAll(
            @ParameterObject @ModelAttribute @Valid CarSearchCriteria criteria,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable
    ) {
        return carService.findAll(criteria, pageable);
    }

    @Operation(summary = "Get a car by id")
    @GetMapping("/{id}")
    public CarResponse findById(@PathVariable Long id) {
        return carService.findById(id);
    }

    @Operation(summary = "Replace a car")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('MANAGER')")
    @PutMapping("/{id}")
    public CarResponse update(
            @PathVariable Long id,
            @RequestBody @Valid CarUpdateRequest request
    ) {
        return carService.update(id, request);
    }

    @Operation(summary = "Partially update a car")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('MANAGER')")
    @PatchMapping("/{id}")
    public CarResponse patch(
            @PathVariable Long id,
            @RequestBody @Valid CarPatchRequest request
    ) {
        return carService.patch(id, request);
    }

    @Operation(summary = "Remove a car from the catalogue")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('MANAGER')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        carService.delete(id);
    }
}
