package com.vdmytriv.carsharing.payment;

public interface CheckoutGateway {

    CheckoutSessionResult create(CheckoutSessionRequest request);
}
