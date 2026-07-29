package com.vdmytriv.carsharing.dto.payment;

import com.vdmytriv.carsharing.model.PaymentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentCreateRequest(
        @NotNull
        @Positive
        Long rentalId,

        @NotNull
        PaymentType type
) {
}
