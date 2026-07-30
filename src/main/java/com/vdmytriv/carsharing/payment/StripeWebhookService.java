package com.vdmytriv.carsharing.payment;

import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.vdmytriv.carsharing.config.StripeProperties;
import com.vdmytriv.carsharing.exception.InvalidRequestException;
import com.vdmytriv.carsharing.service.PaymentService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    private static final Set<String> PAYMENT_EVENTS = Set.of(
            "checkout.session.completed",
            "checkout.session.async_payment_succeeded"
    );

    private final PaymentService paymentService;
    private final StripeProperties stripeProperties;

    public void handle(String payload, String signature) {
        Event event = parseEvent(payload, signature);
        if (!PAYMENT_EVENTS.contains(event.getType())) {
            return;
        }
        StripeObject eventObject = deserializeEventObject(event);
        if (eventObject instanceof Session session
                && "paid".equals(session.getPaymentStatus())) {
            paymentService.markPaid(session.getId());
        }
    }

    private StripeObject deserializeEventObject(Event event) {
        return event.getDataObjectDeserializer()
                .getObject()
                .orElseGet(() -> deserializeUnsafe(event));
    }

    private StripeObject deserializeUnsafe(Event event) {
        try {
            return event.getDataObjectDeserializer().deserializeUnsafe();
        } catch (EventDataObjectDeserializationException exception) {
            throw new InvalidRequestException(
                    "Invalid Stripe webhook payload"
            );
        }
    }

    private Event parseEvent(String payload, String signature) {
        try {
            return Webhook.constructEvent(
                    payload,
                    signature,
                    stripeProperties.webhookSecret()
            );
        } catch (SignatureVerificationException exception) {
            throw new InvalidRequestException(
                    "Invalid Stripe webhook signature"
            );
        }
    }
}
