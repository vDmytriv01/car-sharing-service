package com.vdmytriv.carsharing.dto.payment;

import com.vdmytriv.carsharing.model.PaymentStatus;
import com.vdmytriv.carsharing.model.PaymentType;
import java.math.BigDecimal;

public record PaymentResponse(
        Long id,
        Long userId,
        PaymentStatus status,
        PaymentType type,
        Long rentalId,
        String sessionUrl,
        String sessionId,
        BigDecimal amountToPay
) {
}
