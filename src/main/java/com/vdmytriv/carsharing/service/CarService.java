package com.vdmytriv.carsharing.service;

import com.vdmytriv.carsharing.dto.PageResponse;
import com.vdmytriv.carsharing.dto.car.CarCreateRequest;
import com.vdmytriv.carsharing.dto.car.CarPatchRequest;
import com.vdmytriv.carsharing.dto.car.CarResponse;
import com.vdmytriv.carsharing.dto.car.CarSearchCriteria;
import com.vdmytriv.carsharing.dto.car.CarUpdateRequest;
import com.vdmytriv.carsharing.exception.InvalidRequestException;
import com.vdmytriv.carsharing.exception.ResourceNotFoundException;
import com.vdmytriv.carsharing.mapper.CarMapper;
import com.vdmytriv.carsharing.model.Car;
import com.vdmytriv.carsharing.repository.CarRepository;
import com.vdmytriv.carsharing.repository.CarSpecifications;
import com.vdmytriv.carsharing.validation.PageableValidator;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CarService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id",
            "model",
            "brand",
            "type",
            "inventory",
            "dailyFee"
    );

    private final CarMapper carMapper;
    private final CarRepository carRepository;

    @Transactional
    public CarResponse create(CarCreateRequest request) {
        Car car = carMapper.toModel(request);
        return carMapper.toResponse(carRepository.save(car));
    }

    @Transactional(readOnly = true)
    public PageResponse<CarResponse> findAll(
            CarSearchCriteria criteria,
            Pageable pageable
    ) {
        validateSearch(criteria, pageable);
        Page<CarResponse> page = carRepository
                .findAll(CarSpecifications.withFilters(criteria), pageable)
                .map(carMapper::toResponse);
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public CarResponse findById(Long id) {
        return carMapper.toResponse(findActiveCar(id));
    }

    @Transactional
    public CarResponse update(Long id, CarUpdateRequest request) {
        Car car = findActiveCar(id);
        carMapper.updateModel(car, request);
        return carMapper.toResponse(car);
    }

    @Transactional
    public CarResponse patch(Long id, CarPatchRequest request) {
        validatePatch(request);
        Car car = findActiveCar(id);
        carMapper.patchModel(car, request);
        return carMapper.toResponse(car);
    }

    @Transactional
    public void delete(Long id) {
        carRepository.delete(findActiveCar(id));
    }

    private Car findActiveCar(Long id) {
        return carRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Car", id));
    }

    private void validatePatch(CarPatchRequest request) {
        if (request.model() == null
                && request.brand() == null
                && request.type() == null
                && request.inventory() == null
                && request.dailyFee() == null) {
            throw new InvalidRequestException("At least one car field must be provided");
        }
    }

    private void validateSearch(CarSearchCriteria criteria, Pageable pageable) {
        if (criteria.minDailyFee() != null
                && criteria.maxDailyFee() != null
                && criteria.minDailyFee().compareTo(criteria.maxDailyFee()) > 0) {
            throw new InvalidRequestException(
                    "Minimum daily fee cannot exceed maximum daily fee"
            );
        }
        PageableValidator.validate(pageable, ALLOWED_SORT_FIELDS);
    }
}
