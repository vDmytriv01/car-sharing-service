package com.vdmytriv.carsharing.dto.car;

import com.vdmytriv.carsharing.model.CarType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CarUpdateRequest(
        @NotBlank
        @Size(max = 100)
        String model,

        @NotBlank
        @Size(max = 100)
        String brand,

        @NotNull
        CarType type,

        @NotNull
        @PositiveOrZero
        Integer inventory,

        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 8, fraction = 2)
        BigDecimal dailyFee
) {
}
