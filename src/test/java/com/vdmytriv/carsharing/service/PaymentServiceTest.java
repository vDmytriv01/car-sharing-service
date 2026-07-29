package com.vdmytriv.carsharing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vdmytriv.carsharing.config.PaymentProperties;
import com.vdmytriv.carsharing.exception.InvalidRequestException;
import com.vdmytriv.carsharing.exception.ResourceNotFoundException;
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
import java.math.BigDecimal;
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

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties(
                "usd",
                new BigDecimal("1.50")
        );
        paymentService = new PaymentService(
                checkoutGateway,
                paymentRepository,
                properties,
                rentalRepository,
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

        Payment payment = paymentService.createSession(
                "customer@example.com",
                17L,
                PaymentType.PAYMENT,
                "http://localhost:8080"
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
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getType()).isEqualTo(PaymentType.PAYMENT);
        assertThat(payment.getRental()).isSameAs(rental);
        assertThat(payment.getAmountToPay())
                .isEqualByComparingTo("149.97");
        assertThat(payment.getSessionId()).isEqualTo("cs_test_payment");
        assertThat(payment.getSessionUrl())
                .isEqualTo("https://checkout.stripe.com/c/pay/cs_test_payment");
        verify(paymentRepository).save(payment);
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

        Payment payment = paymentService.createSession(
                "customer@example.com",
                17L,
                PaymentType.PAYMENT,
                "https://car-sharing.example"
        );

        assertThat(payment.getAmountToPay())
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

        Payment payment = paymentService.createSession(
                "customer@example.com",
                17L,
                PaymentType.FINE,
                "https://car-sharing.example"
        );

        ArgumentCaptor<CheckoutSessionRequest> requestCaptor =
                ArgumentCaptor.forClass(CheckoutSessionRequest.class);
        verify(checkoutGateway).create(requestCaptor.capture());
        assertThat(requestCaptor.getValue().amountInCents())
                .isEqualTo(22496L);
        assertThat(payment.getAmountToPay())
                .isEqualByComparingTo("224.96");
        assertThat(payment.getType()).isEqualTo(PaymentType.FINE);
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

        Payment payment = paymentService.createSession(
                "customer@example.com",
                17L,
                PaymentType.FINE,
                "https://car-sharing.example"
        );

        assertThat(payment.getAmountToPay())
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
                PaymentType.FINE,
                "https://car-sharing.example"
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
                PaymentType.PAYMENT,
                "https://car-sharing.example"
        ))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Rental not found: 17");
        verifyNoInteractions(checkoutGateway, paymentRepository);
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
