package com.vdmytriv.carsharing.payment;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.vdmytriv.carsharing.exception.PaymentProviderException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StripeCheckoutGateway implements CheckoutGateway {

    private final StripeClient stripeClient;

    @Override
    public CheckoutSessionResult create(CheckoutSessionRequest request) {
        SessionCreateParams params = buildParams(request);
        try {
            Session session = stripeClient.v1()
                    .checkout()
                    .sessions()
                    .create(params);
            if (session.getId() == null || session.getUrl() == null) {
                throw new PaymentProviderException(
                        "Stripe returned an incomplete checkout session"
                );
            }
            return new CheckoutSessionResult(
                    session.getId(),
                    session.getUrl()
            );
        } catch (StripeException exception) {
            throw new PaymentProviderException(
                    "Could not create Stripe checkout session",
                    exception
            );
        }
    }

    @Override
    public boolean isPaid(String sessionId) {
        try {
            Session session = stripeClient.v1()
                    .checkout()
                    .sessions()
                    .retrieve(sessionId);
            return "paid".equals(session.getPaymentStatus());
        } catch (StripeException exception) {
            throw new PaymentProviderException(
                    "Could not verify Stripe checkout session",
                    exception
            );
        }
    }

    private SessionCreateParams buildParams(CheckoutSessionRequest request) {
        SessionCreateParams.LineItem.PriceData.ProductData productData =
                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                        .setName(request.productName())
                        .build();
        SessionCreateParams.LineItem.PriceData priceData =
                SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency(request.currency())
                        .setUnitAmount(request.amountInCents())
                        .setProductData(productData)
                        .build();
        SessionCreateParams.LineItem lineItem =
                SessionCreateParams.LineItem.builder()
                        .setPriceData(priceData)
                        .setQuantity(request.quantity())
                        .build();
        return SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setCustomerEmail(request.customerEmail())
                .setSuccessUrl(request.successUrl())
                .setCancelUrl(request.cancelUrl())
                .putAllMetadata(request.metadata())
                .addLineItem(lineItem)
                .build();
    }
}
