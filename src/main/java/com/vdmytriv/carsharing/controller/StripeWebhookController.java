package com.vdmytriv.carsharing.controller;

import com.vdmytriv.carsharing.payment.StripeWebhookService;
import io.swagger.v3.oas.annotations.Hidden;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/payments/webhook")
@RequiredArgsConstructor
public class StripeWebhookController {

    private static final String STRIPE_SIGNATURE = "Stripe-Signature";

    private final StripeWebhookService stripeWebhookService;

    @PostMapping
    public void handle(
            @RequestBody byte[] payload,
            @RequestHeader(STRIPE_SIGNATURE) String signature
    ) {
        stripeWebhookService.handle(
                new String(payload, StandardCharsets.UTF_8),
                signature
        );
    }
}
