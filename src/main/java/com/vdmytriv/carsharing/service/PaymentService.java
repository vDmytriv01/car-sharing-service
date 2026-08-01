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
import com.vdmytriv.carsharing.notification.PaymentCompletedEvent;
import com.vdmytriv.carsharing.payment.CheckoutGateway;
import com.vdmytriv.carsharing.payment.CheckoutSessionRequest;
import com.vdmytriv.carsharing.payment.CheckoutSessionResult;
import com.vdmytriv.carsharing.payment.CheckoutSessionStatus;
import com.vdmytriv.carsharing.repository.PaymentRepository;
import com.vdmytriv.carsharing.repository.RentalRepository;
import com.vdmytriv.carsharing.repository.UserRepository;
import com.vdmytriv.carsharing.validation.PageableValidator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final ApplicationEventPublisher eventPublisher;

    public PaymentResponse createSession(
            String email,
            Long rentalId,
            PaymentType type
    ) {
        Rental rental = rentalRepository.findByIdAndUserEmail(rentalId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Rental", rentalId));
        BigDecimal amount = calculateAmount(rental, type);
        Payment existingPayment = paymentRepository.findByRentalIdAndType(
                rentalId,
                type
        ).orElse(null);
        if (existingPayment != null) {
            if (existingPayment.getStatus() == PaymentStatus.PAID) {
                throw new InvalidRequestException(
                        "Payment has already been completed"
                );
            }
            CheckoutSessionStatus sessionStatus = checkoutGateway.getStatus(
                    existingPayment.getSessionId()
            );
            if (sessionStatus == CheckoutSessionStatus.PAID) {
                markPaid(existingPayment.getSessionId());
                throw new InvalidRequestException(
                        "Payment has already been completed"
                );
            }
            if (sessionStatus != CheckoutSessionStatus.EXPIRED) {
                return paymentMapper.toResponse(existingPayment);
            }
        }
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
                ),
                idempotencyKey(rentalId, type, existingPayment)
        );
        CheckoutSessionResult session = checkoutGateway.create(request);

        Payment payment = existingPayment == null
                ? new Payment()
                : existingPayment;
        payment.setStatus(PaymentStatus.PENDING);
        payment.setType(type);
        payment.setRental(rental);
        payment.setSessionUrl(session.sessionUrl());
        payment.setSessionId(session.sessionId());
        payment.setAmountToPay(amount);
        return savePayment(payment, existingPayment == null);
    }

    public void confirmPayment(String sessionId) {
        Payment payment = paymentRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment session",
                        sessionId
                ));
        if (payment.getStatus() == PaymentStatus.PAID) {
            return;
        }
        if (!checkoutGateway.isPaid(sessionId)) {
            throw new InvalidRequestException("Payment has not been completed");
        }
        markPaid(sessionId);
    }

    public void markPaid(String sessionId) {
        int updatedPayments = paymentRepository.markPaid(sessionId);
        if (updatedPayments == 1) {
            eventPublisher.publishEvent(new PaymentCompletedEvent(sessionId));
            return;
        }
        if (updatedPayments == 0
                && !paymentRepository.existsBySessionId(sessionId)) {
            throw new ResourceNotFoundException(
                    "Payment session",
                    sessionId
            );
        }
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
            if (rental.getActualReturnDate() == null) {
                throw new InvalidRequestException(
                        "Rental must be returned before paying a fine"
                );
            }
            long overdueDays = ChronoUnit.DAYS.between(
                    rental.getReturnDate(),
                    rental.getActualReturnDate()
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

    private PaymentResponse savePayment(Payment payment, boolean isNew) {
        try {
            return paymentMapper.toResponse(paymentRepository.save(payment));
        } catch (DataIntegrityViolationException exception) {
            if (!isNew) {
                throw exception;
            }
            return paymentRepository.findByRentalIdAndType(
                            payment.getRental().getId(),
                            payment.getType()
                    )
                    .map(savedPayment -> {
                        if (savedPayment.getStatus() == PaymentStatus.PAID) {
                            throw new InvalidRequestException(
                                    "Payment has already been completed"
                            );
                        }
                        return paymentMapper.toResponse(savedPayment);
                    })
                    .orElseThrow(() -> exception);
        }
    }

    private String idempotencyKey(
            Long rentalId,
            PaymentType type,
            Payment existingPayment
    ) {
        if (existingPayment != null) {
            return "renew-" + existingPayment.getSessionId();
        }
        return "rental-" + rentalId + "-"
                + type.name().toLowerCase();
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
