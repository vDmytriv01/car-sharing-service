package com.vdmytriv.carsharing.service;

import com.vdmytriv.carsharing.config.PaymentProperties;
import com.vdmytriv.carsharing.dto.PageResponse;
import com.vdmytriv.carsharing.dto.payment.PaymentResponse;
import com.vdmytriv.carsharing.exception.InvalidRequestException;
import com.vdmytriv.carsharing.exception.ResourceNotFoundException;
import com.vdmytriv.carsharing.mapper.PaymentMapper;
import com.vdmytriv.carsharing.model.Payment;
import com.vdmytriv.carsharing.model.PaymentStatus;
import com.vdmytriv.carsharing.model.PaymentType;
import com.vdmytriv.carsharing.model.Rental;
import com.vdmytriv.carsharing.model.RoleName;
import com.vdmytriv.carsharing.model.User;
import com.vdmytriv.carsharing.payment.CheckoutGateway;
import com.vdmytriv.carsharing.payment.CheckoutSessionRequest;
import com.vdmytriv.carsharing.payment.CheckoutSessionResult;
import com.vdmytriv.carsharing.repository.PaymentRepository;
import com.vdmytriv.carsharing.repository.RentalRepository;
import com.vdmytriv.carsharing.repository.UserRepository;
import com.vdmytriv.carsharing.validation.PageableValidator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final long CHECKOUT_QUANTITY = 1L;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id",
            "status",
            "type",
            "amountToPay"
    );

    private final CheckoutGateway checkoutGateway;
    private final PaymentMapper paymentMapper;
    private final PaymentRepository paymentRepository;
    private final PaymentProperties paymentProperties;
    private final RentalRepository rentalRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public PaymentResponse createSession(
            String email,
            Long rentalId,
            PaymentType type
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
                successUrl(paymentProperties.baseUrl().toString()),
                cancelUrl(paymentProperties.baseUrl().toString()),
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
        return paymentMapper.toResponse(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> findAll(
            String email,
            Long requestedUserId,
            Pageable pageable
    ) {
        PageableValidator.validate(pageable, ALLOWED_SORT_FIELDS);
        User currentUser = findUserByEmail(email);
        Long userId = currentUser.getRole().getName() == RoleName.MANAGER
                ? requestedUserId
                : currentUser.getId();
        Page<Payment> payments = userId == null
                ? paymentRepository.findAll(pageable)
                : paymentRepository.findAllByRentalUserId(userId, pageable);
        return PageResponse.from(payments.map(paymentMapper::toResponse));
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

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
    }
}
