package com.vdmytriv.carsharing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vdmytriv.carsharing.dto.rental.RentalCreateRequest;
import com.vdmytriv.carsharing.dto.rental.RentalResponse;
import com.vdmytriv.carsharing.mapper.CarMapper;
import com.vdmytriv.carsharing.mapper.RentalMapper;
import com.vdmytriv.carsharing.model.Car;
import com.vdmytriv.carsharing.model.CarType;
import com.vdmytriv.carsharing.model.Rental;
import com.vdmytriv.carsharing.model.User;
import com.vdmytriv.carsharing.notification.RentalCreatedEvent;
import com.vdmytriv.carsharing.repository.CarRepository;
import com.vdmytriv.carsharing.repository.RentalRepository;
import com.vdmytriv.carsharing.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RentalServiceTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private CarRepository carRepository;

    @Mock
    private RentalRepository rentalRepository;

    @Mock
    private UserRepository userRepository;

    private RentalService rentalService;

    @BeforeEach
    void setUp() {
        rentalService = new RentalService(
                carRepository,
                new RentalMapper(new CarMapper()),
                rentalRepository,
                userRepository,
                eventPublisher
        );
    }

    @Test
    void create_WithAvailableCar_PublishesRentalCreatedEvent() {
        Car car = car();
        User user = user();
        LocalDate returnDate = LocalDate.now().plusDays(3);
        when(carRepository.findActiveByIdForUpdate(7L))
                .thenReturn(Optional.of(car));
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(user));
        doAnswer(invocation -> {
            Rental rental = invocation.getArgument(0);
            rental.setId(17L);
            return rental;
        }).when(rentalRepository).save(any(Rental.class));

        RentalResponse response = rentalService.create(
                "customer@example.com",
                new RentalCreateRequest(returnDate, 7L)
        );

        ArgumentCaptor<RentalCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(RentalCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().rentalId()).isEqualTo(17L);
        assertThat(response.id()).isEqualTo(17L);
        assertThat(car.getInventory()).isEqualTo(1);
    }

    private Car car() {
        Car car = new Car();
        car.setId(7L);
        car.setModel("Octavia");
        car.setBrand("Skoda");
        car.setType(CarType.SEDAN);
        car.setInventory(2);
        car.setDailyFee(new BigDecimal("49.99"));
        return car;
    }

    private User user() {
        User user = new User();
        user.setId(5L);
        user.setEmail("customer@example.com");
        return user;
    }
}
