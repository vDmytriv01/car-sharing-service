package com.vdmytriv.carsharing.payment;

public interface CheckoutGateway {

    CheckoutSessionResult create(CheckoutSessionRequest request);

    CheckoutSessionStatus getStatus(String sessionId);

    default boolean isPaid(String sessionId) {
        return getStatus(sessionId) == CheckoutSessionStatus.PAID;
    }
}
