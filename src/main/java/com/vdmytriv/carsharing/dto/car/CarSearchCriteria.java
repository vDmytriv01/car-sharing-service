package com.vdmytriv.carsharing.dto.car;

import com.vdmytriv.carsharing.model.CarType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CarSearchCriteria(
        @Pattern(regexp = ".*\\S.*", message = "must not be blank")
        @Size(max = 100)
        String model,

        @Pattern(regexp = ".*\\S.*", message = "must not be blank")
        @Size(max = 100)
        String brand,

        CarType type,

        Boolean available,

        @DecimalMin("0.01")
        @Digits(integer = 8, fraction = 2)
        BigDecimal minDailyFee,

        @DecimalMin("0.01")
        @Digits(integer = 8, fraction = 2)
        BigDecimal maxDailyFee
) {
}
