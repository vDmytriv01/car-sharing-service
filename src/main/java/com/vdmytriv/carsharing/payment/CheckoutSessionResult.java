package com.vdmytriv.carsharing.payment;

public record CheckoutSessionResult(
        String sessionId,
        String sessionUrl
) {
}
