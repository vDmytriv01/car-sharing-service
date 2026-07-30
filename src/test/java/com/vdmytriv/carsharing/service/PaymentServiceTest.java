package com.vdmytriv.carsharing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
import com.vdmytriv.carsharing.payment.CheckoutSessionStatus;
import com.vdmytriv.carsharing.repository.PaymentRepository;
import com.vdmytriv.carsharing.repository.RentalRepository;
import com.vdmytriv.carsharing.repository.UserRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

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
                userRepository
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
        doAnswer(invocation -> invocation.getArgument(0, Payment.class))
                .when(paymentRepository)
                .save(any(Payment.class));

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
        assertThat(request.idempotencyKey())
                .isEqualTo("rental-17-payment");
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
        verify(paymentRepository).save(any(Payment.class));
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
        doAnswer(invocation -> invocation.getArgument(0, Payment.class))
                .when(paymentRepository)
                .save(any(Payment.class));

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
        doAnswer(invocation -> invocation.getArgument(0, Payment.class))
                .when(paymentRepository)
                .save(any(Payment.class));

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
    void createSession_ForActiveOverdueRental_RejectsFine() {
        Rental rental = rental(
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 25),
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
                .hasMessage("Rental must be returned before paying a fine");

        verifyNoInteractions(checkoutGateway, paymentRepository);
    }

    @Test
    void createSession_ForRentalThatIsNotOverdue_RejectsFine() {
        Rental rental = rental(
                LocalDate.of(2026, 7, 27),
                LocalDate.of(2026, 7, 30),
                LocalDate.of(2026, 7, 29)
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
    void createSession_WhenPendingPaymentExists_ReturnsExistingSession() {
        Rental rental = rental(
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 23),
                null
        );
        Payment existingPayment = payment(rental, PaymentStatus.PENDING);
        when(rentalRepository.findByIdAndUserEmail(17L, "customer@example.com"))
                .thenReturn(Optional.of(rental));
        when(paymentRepository.findByRentalIdAndType(
                17L,
                PaymentType.PAYMENT
        )).thenReturn(Optional.of(existingPayment));
        when(checkoutGateway.getStatus("cs_test_payment"))
                .thenReturn(CheckoutSessionStatus.OPEN);

        PaymentResponse response = paymentService.createSession(
                "customer@example.com",
                17L,
                PaymentType.PAYMENT
        );

        assertThat(response.sessionId()).isEqualTo("cs_test_payment");
        assertThat(response.sessionUrl())
                .isEqualTo("https://checkout.stripe.com/c/pay/cs_test_payment");
        verify(checkoutGateway, never()).create(any());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void createSession_WhenPaidPaymentExists_RejectsDuplicatePayment() {
        Rental rental = rental(
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 23),
                null
        );
        Payment existingPayment = payment(rental, PaymentStatus.PAID);
        when(rentalRepository.findByIdAndUserEmail(17L, "customer@example.com"))
                .thenReturn(Optional.of(rental));
        when(paymentRepository.findByRentalIdAndType(
                17L,
                PaymentType.PAYMENT
        )).thenReturn(Optional.of(existingPayment));

        assertThatThrownBy(() -> paymentService.createSession(
                "customer@example.com",
                17L,
                PaymentType.PAYMENT
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Payment has already been completed");
        verifyNoInteractions(checkoutGateway);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void createSession_WhenPendingSessionExpired_RenewsExistingPayment() {
        Rental rental = rental(
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 23),
                null
        );
        Payment existingPayment = payment(rental, PaymentStatus.PENDING);
        when(rentalRepository.findByIdAndUserEmail(17L, "customer@example.com"))
                .thenReturn(Optional.of(rental));
        when(paymentRepository.findByRentalIdAndType(
                17L,
                PaymentType.PAYMENT
        )).thenReturn(Optional.of(existingPayment));
        when(checkoutGateway.getStatus("cs_test_payment"))
                .thenReturn(CheckoutSessionStatus.EXPIRED);
        when(checkoutGateway.create(any())).thenReturn(
                new CheckoutSessionResult(
                        "cs_test_renewed",
                        "https://checkout.stripe.com/c/pay/cs_test_renewed"
                )
        );
        when(paymentRepository.save(existingPayment))
                .thenReturn(existingPayment);

        PaymentResponse response = paymentService.createSession(
                "customer@example.com",
                17L,
                PaymentType.PAYMENT
        );

        assertThat(response.id()).isEqualTo(21L);
        assertThat(response.sessionId()).isEqualTo("cs_test_renewed");
        assertThat(response.sessionUrl())
                .isEqualTo("https://checkout.stripe.com/c/pay/cs_test_renewed");
        ArgumentCaptor<CheckoutSessionRequest> requestCaptor =
                ArgumentCaptor.forClass(CheckoutSessionRequest.class);
        verify(checkoutGateway).create(requestCaptor.capture());
        assertThat(requestCaptor.getValue().idempotencyKey())
                .isEqualTo("renew-cs_test_payment");
        verify(paymentRepository).save(existingPayment);
    }

    @Test
    void createSession_WhenStripeSessionIsPaid_UpdatesLocalPaymentAndRejects() {
        Rental rental = rental(
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 23),
                null
        );
        Payment existingPayment = payment(rental, PaymentStatus.PENDING);
        when(rentalRepository.findByIdAndUserEmail(17L, "customer@example.com"))
                .thenReturn(Optional.of(rental));
        when(paymentRepository.findByRentalIdAndType(
                17L,
                PaymentType.PAYMENT
        )).thenReturn(Optional.of(existingPayment));
        when(checkoutGateway.getStatus("cs_test_payment"))
                .thenReturn(CheckoutSessionStatus.PAID);
        when(paymentRepository.markPaid("cs_test_payment")).thenReturn(1);

        assertThatThrownBy(() -> paymentService.createSession(
                "customer@example.com",
                17L,
                PaymentType.PAYMENT
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Payment has already been completed");
        verify(paymentRepository).markPaid("cs_test_payment");
        verify(checkoutGateway, never()).create(any());
    }

    @Test
    void createSession_WhenConcurrentRequestSavedFirst_ReturnsSavedPayment() {
        Rental rental = rental(
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 23),
                null
        );
        Payment savedPayment = payment(rental, PaymentStatus.PENDING);
        when(rentalRepository.findByIdAndUserEmail(17L, "customer@example.com"))
                .thenReturn(Optional.of(rental));
        when(paymentRepository.findByRentalIdAndType(
                17L,
                PaymentType.PAYMENT
        ))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(savedPayment));
        when(checkoutGateway.create(any())).thenReturn(
                new CheckoutSessionResult(
                        "cs_test_payment",
                        "https://checkout.stripe.com/c/pay/cs_test_payment"
                )
        );
        doThrow(new DataIntegrityViolationException("duplicate payment"))
                .when(paymentRepository)
                .save(any(Payment.class));

        PaymentResponse response = paymentService.createSession(
                "customer@example.com",
                17L,
                PaymentType.PAYMENT
        );

        assertThat(response.id()).isEqualTo(21L);
        assertThat(response.sessionId()).isEqualTo("cs_test_payment");
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void createSession_WhenConcurrentPaymentIsAlreadyPaid_RejectsDuplicate() {
        Rental rental = rental(
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 23),
                null
        );
        Payment savedPayment = payment(rental, PaymentStatus.PAID);
        when(rentalRepository.findByIdAndUserEmail(17L, "customer@example.com"))
                .thenReturn(Optional.of(rental));
        when(paymentRepository.findByRentalIdAndType(
                17L,
                PaymentType.PAYMENT
        ))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(savedPayment));
        when(checkoutGateway.create(any())).thenReturn(
                new CheckoutSessionResult(
                        "cs_test_payment",
                        "https://checkout.stripe.com/c/pay/cs_test_payment"
                )
        );
        doThrow(new DataIntegrityViolationException("duplicate payment"))
                .when(paymentRepository)
                .save(any(Payment.class));

        assertThatThrownBy(() -> paymentService.createSession(
                "customer@example.com",
                17L,
                PaymentType.PAYMENT
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Payment has already been completed");
        verify(paymentRepository).save(any(Payment.class));
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

    private Payment payment(Rental rental, PaymentStatus status) {
        Payment payment = payment(status);
        payment.setId(21L);
        payment.setType(PaymentType.PAYMENT);
        payment.setRental(rental);
        payment.setSessionUrl(
                "https://checkout.stripe.com/c/pay/cs_test_payment"
        );
        payment.setAmountToPay(new BigDecimal("149.97"));
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
