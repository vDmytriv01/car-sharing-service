package com.vdmytriv.carsharing.notification;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vdmytriv.carsharing.exception.NotificationException;
import com.vdmytriv.carsharing.model.Car;
import com.vdmytriv.carsharing.model.Rental;
import com.vdmytriv.carsharing.model.User;
import com.vdmytriv.carsharing.repository.RentalRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OverdueRentalNotificationSchedulerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 1);

    @Mock
    private NotificationService notificationService;

    @Mock
    private RentalRepository rentalRepository;

    private OverdueRentalNotificationScheduler scheduler;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-01T08:00:00Z"),
                ZoneOffset.UTC
        );
        scheduler = new OverdueRentalNotificationScheduler(
                notificationService,
                rentalRepository,
                clock
        );
    }

    @Test
    void notifyAboutOverdueRentals_WithRentalsDueByTomorrow_SendsEachRental() {
        Rental firstRental = rental(
                17L,
                LocalDate.of(2026, 7, 28),
                LocalDate.of(2026, 7, 31)
        );
        when(rentalRepository
                .findAllByActualReturnDateIsNullAndReturnDateLessThanEqual(
                        TODAY.plusDays(1)
                ))
                .thenReturn(List.of(firstRental));

        scheduler.notifyAboutOverdueRentals();

        InOrder inOrder = inOrder(notificationService);
        inOrder.verify(notificationService).send("""
                Overdue rental
                Rental ID: 17
                Customer ID: 5
                Car: Skoda Octavia (#7)
                Rental date: 2026-07-28
                Return date: 2026-07-31""");
    }

    @Test
    void notifyAboutOverdueRentals_WithNoOverdueRentals_SendsSummary() {
        when(rentalRepository
                .findAllByActualReturnDateIsNullAndReturnDateLessThanEqual(
                        TODAY.plusDays(1)
                ))
                .thenReturn(List.of());

        scheduler.notifyAboutOverdueRentals();

        verify(notificationService).send("No rentals overdue today!");
    }

    @Test
    void notifyAboutOverdueRentals_WhenOneMessageFails_ContinuesSending() {
        Rental firstRental = rental(
                17L,
                LocalDate.of(2026, 7, 28),
                LocalDate.of(2026, 7, 31)
        );
        Rental secondRental = rental(
                18L,
                LocalDate.of(2026, 7, 30),
                LocalDate.of(2026, 7, 31)
        );
        when(rentalRepository
                .findAllByActualReturnDateIsNullAndReturnDateLessThanEqual(
                        TODAY.plusDays(1)
                ))
                .thenReturn(List.of(firstRental, secondRental));
        doThrow(new NotificationException("Telegram unavailable"))
                .doNothing()
                .when(notificationService)
                .send(anyString());

        scheduler.notifyAboutOverdueRentals();

        verify(notificationService, times(2))
                .send(anyString());
    }

    private Rental rental(
            Long rentalId,
            LocalDate rentalDate,
            LocalDate returnDate
    ) {
        User user = new User();
        user.setId(5L);
        Car car = new Car();
        car.setId(7L);
        car.setBrand("Skoda");
        car.setModel("Octavia");
        Rental rental = new Rental();
        rental.setId(rentalId);
        rental.setRentalDate(rentalDate);
        rental.setReturnDate(returnDate);
        rental.setCar(car);
        rental.setUser(user);
        return rental;
    }
}
