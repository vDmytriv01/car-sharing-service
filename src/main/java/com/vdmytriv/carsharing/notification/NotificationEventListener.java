package com.vdmytriv.carsharing.notification;

import com.vdmytriv.carsharing.config.PaymentProperties;
import com.vdmytriv.carsharing.exception.NotificationException;
import com.vdmytriv.carsharing.exception.ResourceNotFoundException;
import com.vdmytriv.carsharing.model.Payment;
import com.vdmytriv.carsharing.model.Rental;
import com.vdmytriv.carsharing.repository.PaymentRepository;
import com.vdmytriv.carsharing.repository.RentalRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final PaymentRepository paymentRepository;
    private final RentalRepository rentalRepository;
    private final PaymentProperties paymentProperties;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    public void handleRentalCreated(RentalCreatedEvent event) {
        Rental rental = rentalRepository.findById(event.rentalId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rental",
                        event.rentalId()
                ));
        String message = """
                New rental created
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
        sendSafely(message, "rental");
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        Payment payment = paymentRepository.findBySessionId(event.sessionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment session",
                        event.sessionId()
                ));
        String message = """
                Payment completed
                Payment ID: %d
                Rental ID: %d
                Customer ID: %d
                Type: %s
                Amount: %s %s""".formatted(
                payment.getId(),
                payment.getRental().getId(),
                payment.getRental().getUser().getId(),
                payment.getType(),
                payment.getAmountToPay(),
                paymentProperties.currency().toUpperCase(Locale.ROOT)
        );
        sendSafely(message, "payment");
    }

    private void sendSafely(String message, String notificationType) {
        try {
            notificationService.send(message);
        } catch (NotificationException exception) {
            log.warn(
                    "Could not send {} notification: {}",
                    notificationType,
                    exception.getMessage()
            );
        }
    }
}
