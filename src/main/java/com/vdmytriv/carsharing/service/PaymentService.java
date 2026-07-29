package com.vdmytriv.carsharing.service;

import com.vdmytriv.carsharing.config.PaymentProperties;
import com.vdmytriv.carsharing.exception.InvalidRequestException;
import com.vdmytriv.carsharing.exception.ResourceNotFoundException;
import com.vdmytriv.carsharing.model.Payment;
import com.vdmytriv.carsharing.model.PaymentStatus;
import com.vdmytriv.carsharing.model.PaymentType;
import com.vdmytriv.carsharing.model.Rental;
import com.vdmytriv.carsharing.payment.CheckoutGateway;
import com.vdmytriv.carsharing.payment.CheckoutSessionRequest;
import com.vdmytriv.carsharing.payment.CheckoutSessionResult;
import com.vdmytriv.carsharing.repository.PaymentRepository;
import com.vdmytriv.carsharing.repository.RentalRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final long CHECKOUT_QUANTITY = 1L;

    private final CheckoutGateway checkoutGateway;
    private final PaymentRepository paymentRepository;
    private final PaymentProperties paymentProperties;
    private final RentalRepository rentalRepository;
    private final Clock clock;

    public Payment createSession(
            String email,
            Long rentalId,
            PaymentType type,
            String baseUrl
    ) {
        Rental rental = rentalRepository.findByIdAndUserEmail(rentalId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Rental", rentalId));
        BigDecimal amount = calculateAmount(rental, type);
        CheckoutSessionRequest request = new CheckoutSessionRequest(
                toCents(amount),
                paymentProperties.currency(),
                CHECKOUT_QUANTITY,
                productName(rentalId, type),
                email,
                successUrl(baseUrl),
                cancelUrl(baseUrl),
                Map.of(
                        "rentalId", rentalId.toString(),
                        "paymentType", type.name()
                )
        );
        CheckoutSessionResult session = checkoutGateway.create(request);

        Payment payment = new Payment();
        payment.setStatus(PaymentStatus.PENDING);
        payment.setType(type);
        payment.setRental(rental);
        payment.setSessionUrl(session.sessionUrl());
        payment.setSessionId(session.sessionId());
        payment.setAmountToPay(amount);
        return paymentRepository.save(payment);
    }

    private BigDecimal calculateAmount(Rental rental, PaymentType type) {
        if (type == PaymentType.FINE) {
            LocalDate fineEndDate = rental.getActualReturnDate() == null
                    ? LocalDate.now(clock)
                    : rental.getActualReturnDate();
            long overdueDays = ChronoUnit.DAYS.between(
                    rental.getReturnDate(),
                    fineEndDate
            );
            if (overdueDays <= 0) {
                throw new InvalidRequestException("Rental is not overdue");
            }
            return rental.getCar().getDailyFee()
                    .multiply(BigDecimal.valueOf(overdueDays))
                    .multiply(paymentProperties.fineMultiplier())
                    .setScale(2, RoundingMode.HALF_UP);
        }
        long rentalDays = Math.max(1, ChronoUnit.DAYS.between(
                rental.getRentalDate(),
                rental.getReturnDate()
        ));
        return rental.getCar().getDailyFee()
                .multiply(BigDecimal.valueOf(rentalDays))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String cancelUrl(String baseUrl) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .path("/payments/cancel")
                .build()
                .toUriString();
    }

    private String productName(Long rentalId, PaymentType type) {
        return "Car rental " + type.name().toLowerCase()
                + " #" + rentalId;
    }

    private String successUrl(String baseUrl) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .path("/payments/success")
                .queryParam("session_id", "{CHECKOUT_SESSION_ID}")
                .build()
                .toUriString();
    }

    private long toCents(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }
}
