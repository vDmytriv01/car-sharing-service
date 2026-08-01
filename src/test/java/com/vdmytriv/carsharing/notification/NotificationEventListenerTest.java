package com.vdmytriv.carsharing.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vdmytriv.carsharing.config.PaymentProperties;
import com.vdmytriv.carsharing.exception.NotificationException;
import com.vdmytriv.carsharing.model.Car;
import com.vdmytriv.carsharing.model.Payment;
import com.vdmytriv.carsharing.model.PaymentStatus;
import com.vdmytriv.carsharing.model.PaymentType;
import com.vdmytriv.carsharing.model.Rental;
import com.vdmytriv.carsharing.model.User;
import com.vdmytriv.carsharing.repository.PaymentRepository;
import com.vdmytriv.carsharing.repository.RentalRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RentalRepository rentalRepository;

    private NotificationEventListener eventListener;

    @BeforeEach
    void setUp() {
        PaymentProperties paymentProperties = new PaymentProperties(
                URI.create("http://localhost:8080"),
                "usd",
                new BigDecimal("1.50")
        );
        eventListener = new NotificationEventListener(
                notificationService,
                paymentRepository,
                rentalRepository,
                paymentProperties
        );
    }

    @Test
    void handleRentalCreated_WithExistingRental_SendsDetails() {
        when(rentalRepository.findById(17L))
                .thenReturn(Optional.of(rental()));

        eventListener.handleRentalCreated(new RentalCreatedEvent(17L));

        verify(notificationService).send("""
                New rental created
                Rental ID: 17
                Customer ID: 5
                Car: Skoda Octavia (#7)
                Rental date: 2026-08-01
                Return date: 2026-08-04""");
    }

    @Test
    void handlePaymentCompleted_WithExistingPayment_SendsDetails() {
        when(paymentRepository.findBySessionId("cs_test_payment"))
                .thenReturn(Optional.of(payment()));

        eventListener.handlePaymentCompleted(
                new PaymentCompletedEvent("cs_test_payment")
        );

        verify(notificationService).send("""
                Payment completed
                Payment ID: 21
                Rental ID: 17
                Customer ID: 5
                Type: PAYMENT
                Amount: 149.97 USD""");
    }

    @Test
    void handleRentalCreated_WhenTelegramFails_DoesNotPropagateFailure() {
        when(rentalRepository.findById(17L))
                .thenReturn(Optional.of(rental()));
        doThrow(new NotificationException("Telegram unavailable"))
                .when(notificationService)
                .send(org.mockito.ArgumentMatchers.anyString());

        assertThatCode(() -> eventListener.handleRentalCreated(
                new RentalCreatedEvent(17L)
        )).doesNotThrowAnyException();
    }

    private Payment payment() {
        Payment payment = new Payment();
        payment.setId(21L);
        payment.setStatus(PaymentStatus.PAID);
        payment.setType(PaymentType.PAYMENT);
        payment.setRental(rental());
        payment.setSessionId("cs_test_payment");
        payment.setSessionUrl("https://checkout.stripe.com/test");
        payment.setAmountToPay(new BigDecimal("149.97"));
        return payment;
    }

    private Rental rental() {
        User user = new User();
        user.setId(5L);
        Car car = new Car();
        car.setId(7L);
        car.setBrand("Skoda");
        car.setModel("Octavia");
        Rental rental = new Rental();
        rental.setId(17L);
        rental.setRentalDate(LocalDate.of(2026, 8, 1));
        rental.setReturnDate(LocalDate.of(2026, 8, 4));
        rental.setCar(car);
        rental.setUser(user);
        return rental;
    }
}
