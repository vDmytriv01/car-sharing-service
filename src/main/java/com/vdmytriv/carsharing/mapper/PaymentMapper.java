package com.vdmytriv.carsharing.mapper;

import com.vdmytriv.carsharing.dto.payment.PaymentResponse;
import com.vdmytriv.carsharing.model.Payment;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getRental().getUser().getId(),
                payment.getStatus(),
                payment.getType(),
                payment.getRental().getId(),
                payment.getSessionUrl(),
                payment.getSessionId(),
                payment.getAmountToPay()
        );
    }
}
