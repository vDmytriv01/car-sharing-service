package com.vdmytriv.carsharing.payment;

import java.util.Map;

public record CheckoutSessionRequest(
        long amountInCents,
        String currency,
        long quantity,
        String productName,
        String customerEmail,
        String successUrl,
        String cancelUrl,
        Map<String, String> metadata,
        String idempotencyKey
) {

    public CheckoutSessionRequest {
        metadata = Map.copyOf(metadata);
    }
}
