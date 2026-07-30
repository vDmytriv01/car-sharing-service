package com.vdmytriv.carsharing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vdmytriv.carsharing.config.PaymentProperties;
import com.vdmytriv.carsharing.dto.payment.PaymentResponse;
import com.vdmytriv.carsharing.exception.InvalidRequestException;
import com.vdmytriv.carsharing.exception.ResourceNotFoundException;
import com.vdmytriv.carsharing.mapper.PaymentMapper;
import com.vdmytriv.carsharing.model.Car;
import com.vdmytriv.carsharing.model.Payment;
import com.vdmytriv.carsharing.model.PaymentStatus;
import com.vdmytriv.carsharing.model.PaymentType;
import com.vdmytriv.carsharing.model.Rental;
import com.vdmytriv.carsharing.model.User;
import com.vdmytriv.carsharing.payment.CheckoutGateway;
import com.vdmytriv.carsharing.payment.CheckoutSessionRequest;
import com.vdmytriv.carsharing.payment.CheckoutSessionResult;
import com.vdmytriv.carsharing.repository.PaymentRepository;
import com.vdmytriv.carsharing.repository.RentalRepository;
import com.vdmytriv.carsharing.repository.UserRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-29T10:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private CheckoutGateway checkoutGateway;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RentalRepository rentalRepository;

    @Mock
    private UserRepository userRepository;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties(
                URI.create("http://localhost:8080"),
                "usd",
                new BigDecimal("1.50")
        );
        paymentService = new PaymentService(
                checkoutGateway,
                new PaymentMapper(),
                paymentRepository,
                properties,
                rentalRepository,
                userRepository,
                CLOCK
        );
    }

    @Test
    void createSession_ForRentalPayment_CalculatesAmountAndStoresSession() {
        Rental rental = rental(
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 23),
                null
        );
        when(rentalRepository.findByIdAndUserEmail(17L, "customer@example.com"))
                .thenReturn(Optional.of(rental));
        when(checkoutGateway.create(any()))
                .thenReturn(new CheckoutSessionResult(
                        "cs_test_payment",
                        "https://checkout.stripe.com/c/pay/cs_test_payment"
                ));
        when(paymentRepository.save(any())).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        PaymentResponse payment = paymentService.createSession(
                "customer@example.com",
                17L,
                PaymentType.PAYMENT
        );

        ArgumentCaptor<CheckoutSessionRequest> requestCaptor =
                ArgumentCaptor.forClass(CheckoutSessionRequest.class);
        verify(checkoutGateway).create(requestCaptor.capture());
        CheckoutSessionRequest request = requestCaptor.getValue();
        assertThat(request.amountInCents()).isEqualTo(14997L);
        assertThat(request.currency()).isEqualTo("usd");
        assertThat(request.quantity()).isEqualTo(1L);
        assertThat(request.customerEmail()).isEqualTo("customer@example.com");
        assertThat(request.metadata()).containsExactlyInAnyOrderEntriesOf(
                Map.of(
                        "rentalId", "17",
                        "paymentType", "PAYMENT"
                )
        );
        assertThat(request.successUrl()).isEqualTo(
                "http://localhost:8080/payments/success"
                        + "?session_id={CHECKOUT_SESSION_ID}"
        );
        assertThat(request.cancelUrl())
                .isEqualTo("http://localhost:8080/payments/cancel");
        assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.type()).isEqualTo(PaymentType.PAYMENT);
        assertThat(payment.rentalId()).isEqualTo(rental.getId());
        assertThat(payment.amountToPay())
                .isEqualByComparingTo("149.97");
        assertThat(payment.sessionId()).isEqualTo("cs_test_payment");
        assertThat(payment.sessionUrl())
                .isEqualTo("https://checkout.stripe.com/c/pay/cs_test_payment");
        verify(paymentRepository).save(any());
    }

    @Test
    void createSession_ForSameDayRental_ChargesOneBillingDay() {
        Rental rental = rental(
                LocalDate.of(2026, 7, 29),
                LocalDate.of(2026, 7, 29),
                null
        );
        when(rentalRepository.findByIdAndUserEmail(17L, "customer@example.com"))
                .thenReturn(Optional.of(rental));
        when(checkoutGateway.create(any()))
                .thenReturn(new CheckoutSessionResult(
                        "cs_test_same_day",
                        "https://checkout.stripe.com/c/pay/cs_test_same_day"
                ));
        when(paymentRepository.save(any())).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        PaymentResponse payment = paymentService.createSession(
                "customer@example.com",
                17L,
                PaymentType.PAYMENT
        );

        assertThat(payment.amountToPay())
                .isEqualByComparingTo("49.99");
    }

    @Test
    void createSession_ForOverdueFine_AppliesMultiplierAndRoundsToCents() {
        Rental rental = rental(
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 25),
                LocalDate.of(2026, 7, 28)
        );
        when(rentalRepository.findByIdAndUserEmail(17L, "customer@example.com"))
                .thenReturn(Optional.of(rental));
        when(checkoutGateway.create(any()))
                .thenReturn(new CheckoutSessionResult(
                        "cs_test_fine",
                        "https://checkout.stripe.com/c/pay/cs_test_fine"
                ));
        when(paymentRepository.save(any())).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        PaymentResponse payment = paymentService.createSession(
                "customer@example.com",
                17L,
                PaymentType.FINE
        );

        ArgumentCaptor<CheckoutSessionRequest> requestCaptor =
                ArgumentCaptor.forClass(CheckoutSessionRequest.class);
        verify(checkoutGateway).create(requestCaptor.capture());
        assertThat(requestCaptor.getValue().amountInCents())
                .isEqualTo(22496L);
        assertThat(payment.amountToPay())
                .isEqualByComparingTo("224.96");
        assertThat(payment.type()).isEqualTo(PaymentType.FINE);
    }

    @Test
    void createSession_ForActiveOverdueRental_UsesCurrentDateForFine() {
        Rental rental = rental(
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 25),
                null
        );
        when(rentalRepository.findByIdAndUserEmail(17L, "customer@example.com"))
                .thenReturn(Optional.of(rental));
        when(checkoutGateway.create(any()))
                .thenReturn(new CheckoutSessionResult(
                        "cs_test_active_fine",
                        "https://checkout.stripe.com/c/pay/cs_test_active_fine"
                ));
        when(paymentRepository.save(any())).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        PaymentResponse payment = paymentService.createSession(
                "customer@example.com",
                17L,
                PaymentType.FINE
        );

        assertThat(payment.amountToPay())
                .isEqualByComparingTo("299.94");
    }

    @Test
    void createSession_ForRentalThatIsNotOverdue_RejectsFine() {
        Rental rental = rental(
                LocalDate.of(2026, 7, 27),
                LocalDate.of(2026, 7, 30),
                null
        );
        when(rentalRepository.findByIdAndUserEmail(17L, "customer@example.com"))
                .thenReturn(Optional.of(rental));

        assertThatThrownBy(() -> paymentService.createSession(
                "customer@example.com",
                17L,
                PaymentType.FINE
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Rental is not overdue");
        verifyNoInteractions(checkoutGateway, paymentRepository);
    }

    @Test
    void createSession_ForRentalNotOwnedByCustomer_ReturnsNotFound() {
        when(rentalRepository.findByIdAndUserEmail(17L, "customer@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createSession(
                "customer@example.com",
                17L,
                PaymentType.PAYMENT
        ))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Rental not found: 17");
        verifyNoInteractions(checkoutGateway, paymentRepository);
    }

    @Test
    void confirmPayment_WhenStripeSessionIsPaid_UpdatesPaymentStatus() {
        Payment payment = payment(PaymentStatus.PENDING);
        when(paymentRepository.findBySessionId("cs_test_payment"))
                .thenReturn(Optional.of(payment));
        when(checkoutGateway.isPaid("cs_test_payment")).thenReturn(true);
        when(paymentRepository.markPaid("cs_test_payment")).thenReturn(1);

        paymentService.confirmPayment("cs_test_payment");

        verify(paymentRepository).markPaid("cs_test_payment");
    }

    @Test
    void confirmPayment_WhenStripeSessionIsUnpaid_RejectsUpdate() {
        Payment payment = payment(PaymentStatus.PENDING);
        when(paymentRepository.findBySessionId("cs_test_payment"))
                .thenReturn(Optional.of(payment));
        when(checkoutGateway.isPaid("cs_test_payment")).thenReturn(false);

        assertThatThrownBy(() ->
                paymentService.confirmPayment("cs_test_payment"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Payment has not been completed");
        verify(paymentRepository).findBySessionId("cs_test_payment");
    }

    @Test
    void confirmPayment_WhenPaymentIsAlreadyPaid_SkipsStripeRequest() {
        Payment payment = payment(PaymentStatus.PAID);
        when(paymentRepository.findBySessionId("cs_test_payment"))
                .thenReturn(Optional.of(payment));

        paymentService.confirmPayment("cs_test_payment");

        verifyNoInteractions(checkoutGateway);
        verify(paymentRepository).findBySessionId("cs_test_payment");
    }

    @Test
    void confirmPayment_WhenSessionDoesNotExist_SkipsStripeRequest() {
        when(paymentRepository.findBySessionId("cs_test_missing"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                paymentService.confirmPayment("cs_test_missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Payment session not found: cs_test_missing");
        verifyNoInteractions(checkoutGateway);
    }

    @Test
    void markPaid_WhenPaymentIsAlreadyPaid_DoesNotSaveAgain() {
        when(paymentRepository.markPaid("cs_test_payment")).thenReturn(0);
        when(paymentRepository.existsBySessionId("cs_test_payment"))
                .thenReturn(true);

        paymentService.markPaid("cs_test_payment");

        verify(paymentRepository).markPaid("cs_test_payment");
        verify(paymentRepository).existsBySessionId("cs_test_payment");
    }

    @Test
    void markPaid_WhenSessionDoesNotExist_ReturnsNotFound() {
        when(paymentRepository.markPaid("cs_test_missing")).thenReturn(0);
        when(paymentRepository.existsBySessionId("cs_test_missing"))
                .thenReturn(false);

        assertThatThrownBy(() -> paymentService.markPaid("cs_test_missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Payment session not found: cs_test_missing");
    }

    private Payment payment(PaymentStatus status) {
        Payment payment = new Payment();
        payment.setStatus(status);
        payment.setSessionId("cs_test_payment");
        return payment;
    }

    private Rental rental(
            LocalDate rentalDate,
            LocalDate returnDate,
            LocalDate actualReturnDate
    ) {
        User user = new User();
        user.setEmail("customer@example.com");
        Car car = new Car();
        car.setModel("Octavia");
        car.setBrand("Skoda");
        car.setDailyFee(new BigDecimal("49.99"));
        Rental rental = new Rental();
        rental.setId(17L);
        rental.setRentalDate(rentalDate);
        rental.setReturnDate(returnDate);
        rental.setActualReturnDate(actualReturnDate);
        rental.setCar(car);
        rental.setUser(user);
        return rental;
    }
}
