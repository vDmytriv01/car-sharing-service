package com.vdmytriv.carsharing.service;

import com.vdmytriv.carsharing.dto.PageResponse;
import com.vdmytriv.carsharing.dto.rental.RentalCreateRequest;
import com.vdmytriv.carsharing.dto.rental.RentalResponse;
import com.vdmytriv.carsharing.exception.InvalidRequestException;
import com.vdmytriv.carsharing.exception.ResourceNotFoundException;
import com.vdmytriv.carsharing.mapper.RentalMapper;
import com.vdmytriv.carsharing.model.Car;
import com.vdmytriv.carsharing.model.Rental;
import com.vdmytriv.carsharing.model.RoleName;
import com.vdmytriv.carsharing.model.User;
import com.vdmytriv.carsharing.notification.RentalCreatedEvent;
import com.vdmytriv.carsharing.repository.CarRepository;
import com.vdmytriv.carsharing.repository.RentalRepository;
import com.vdmytriv.carsharing.repository.RentalSpecifications;
import com.vdmytriv.carsharing.repository.UserRepository;
import com.vdmytriv.carsharing.validation.PageableValidator;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RentalService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id",
            "rentalDate",
            "returnDate",
            "actualReturnDate"
    );

    private final CarRepository carRepository;
    private final RentalMapper rentalMapper;
    private final RentalRepository rentalRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public RentalResponse create(String email, RentalCreateRequest request) {
        Car car = carRepository.findActiveByIdForUpdate(request.carId())
                .orElseThrow(() -> new ResourceNotFoundException("Car", request.carId()));
        if (car.getInventory() == 0) {
            throw new InvalidRequestException("Car is not available for rental");
        }

        car.setInventory(car.getInventory() - 1);
        User user = findUserByEmail(email);
        Rental rental = new Rental();
        rental.setRentalDate(LocalDate.now());
        rental.setReturnDate(request.returnDate());
        rental.setCar(car);
        rental.setUser(user);
        Rental savedRental = rentalRepository.save(rental);
        eventPublisher.publishEvent(new RentalCreatedEvent(
                savedRental.getId()
        ));
        return rentalMapper.toResponse(savedRental);
    }

    @Transactional(readOnly = true)
    public PageResponse<RentalResponse> findAll(
            String email,
            Long requestedUserId,
            Boolean active,
            Pageable pageable
    ) {
        validatePageable(pageable);
        User currentUser = findUserByEmail(email);
        Long userId = currentUser.getRole().getName() == RoleName.MANAGER
                ? requestedUserId
                : currentUser.getId();
        Page<RentalResponse> page = rentalRepository
                .findAll(RentalSpecifications.withFilters(userId, active), pageable)
                .map(rentalMapper::toResponse);
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public RentalResponse findById(String email, Long rentalId) {
        User currentUser = findUserByEmail(email);
        Rental rental = currentUser.getRole().getName() == RoleName.MANAGER
                ? rentalRepository.findById(rentalId).orElseThrow(
                        () -> new ResourceNotFoundException("Rental", rentalId)
                )
                : rentalRepository.findByIdAndUserId(rentalId, currentUser.getId())
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Rental", rentalId)
                        );
        return rentalMapper.toResponse(rental);
    }

    @Transactional
    public RentalResponse returnRental(String email, Long rentalId) {
        User currentUser = findUserByEmail(email);
        Rental rental = rentalRepository.findByIdForUpdate(rentalId)
                .orElseThrow(() -> new ResourceNotFoundException("Rental", rentalId));
        if (currentUser.getRole().getName() != RoleName.MANAGER
                && !rental.getUser().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("Rental", rentalId);
        }
        if (rental.getActualReturnDate() != null) {
            throw new InvalidRequestException("Rental has already been returned");
        }

        Long carId = rental.getCar().getId();
        Car car = carRepository.findByIdForUpdate(carId)
                .orElseThrow(() -> new IllegalStateException(
                        "Rental car not found: " + carId
                ));
        rental.setActualReturnDate(LocalDate.now());
        car.setInventory(car.getInventory() + 1);
        return rentalMapper.toResponse(rental);
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
    }

    private void validatePageable(Pageable pageable) {
        PageableValidator.validate(pageable, ALLOWED_SORT_FIELDS);
    }
}
