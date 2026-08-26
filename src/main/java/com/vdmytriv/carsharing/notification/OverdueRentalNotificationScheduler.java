package com.vdmytriv.carsharing.notification;

import com.vdmytriv.carsharing.exception.NotificationException;
import com.vdmytriv.carsharing.model.Rental;
import com.vdmytriv.carsharing.repository.RentalRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueRentalNotificationScheduler {

    private final NotificationService notificationService;
    private final RentalRepository rentalRepository;
    private final Clock clock;

    @Scheduled(
            cron = "${notification.overdue-rentals.cron}",
            zone = "${notification.overdue-rentals.zone}"
    )
    public void notifyAboutOverdueRentals() {
        LocalDate latestReturnDate = LocalDate.now(clock).plusDays(1);
        List<Rental> rentals = rentalRepository
                .findAllByActualReturnDateIsNullAndReturnDateLessThanEqual(
                        latestReturnDate
                );

        if (rentals.isEmpty()) {
            sendSafely("No rentals overdue today!");
            return;
        }

        rentals.forEach(rental -> sendSafely(createMessage(rental)));
    }

    private String createMessage(Rental rental) {
        return """
                Overdue rental
                Rental ID: %d
                Customer ID: %d
                Car: %s %s (#%d)
                Rental date: %s
                Return date: %s""".formatted(
                rental.getId(),
                rental.getUser().getId(),
                rental.getCar().getBrand(),
                rental.getCar().getModel(),
                rental.getCar().getId(),
                rental.getRentalDate(),
                rental.getReturnDate()
        );
    }

    private void sendSafely(String message) {
        try {
            notificationService.send(message);
        } catch (NotificationException exception) {
            log.warn(
                    "Could not send overdue rental notification: {}",
                    exception.getMessage()
            );
        }
    }
}
