package com.vdmytriv.carsharing.dto.car;

import com.vdmytriv.carsharing.model.CarType;
import java.math.BigDecimal;

public record CarResponse(
        Long id,
        String model,
        String brand,
        CarType type,
        int inventory,
        BigDecimal dailyFee
) {
}
