package com.vdmytriv.carsharing.controller;

import com.vdmytriv.carsharing.dto.payment.PaymentMessageResponse;
import com.vdmytriv.carsharing.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payment callbacks", description = "Stripe payment callbacks")
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentCallbackController {

    private final PaymentService paymentService;

    @Operation(summary = "Confirm a completed Stripe payment")
    @GetMapping("/success")
    public PaymentMessageResponse success(
            @RequestParam(name = "session_id") String sessionId
    ) {
        paymentService.confirmPayment(sessionId);
        return new PaymentMessageResponse("Payment completed successfully");
    }

    @Operation(summary = "Handle a cancelled Stripe checkout")
    @GetMapping("/cancel")
    public PaymentMessageResponse cancel() {
        return new PaymentMessageResponse(
                "Payment was cancelled. You can try again later"
        );
    }
}
