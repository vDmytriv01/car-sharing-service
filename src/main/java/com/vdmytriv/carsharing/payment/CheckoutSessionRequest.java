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
        Map<String, String> metadata
) {

    public CheckoutSessionRequest {
        metadata = Map.copyOf(metadata);
    }
}
