package com.vdmytriv.carsharing.payment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.vdmytriv.carsharing.config.StripeProperties;
import com.vdmytriv.carsharing.exception.InvalidRequestException;
import com.vdmytriv.carsharing.service.PaymentService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

    private static final String WEBHOOK_SECRET = "whsec_test";

    @Mock
    private PaymentService paymentService;

    private StripeWebhookService webhookService;

    @BeforeEach
    void setUp() {
        StripeProperties properties = new StripeProperties(
                "sk_test",
                WEBHOOK_SECRET
        );
        webhookService = new StripeWebhookService(paymentService, properties);
    }

    @Test
    void handle_WithPaidCheckoutEvent_MarksPaymentPaid() throws Exception {
        String payload = eventPayload("paid");

        webhookService.handle(payload, signature(payload));

        verify(paymentService).markPaid("cs_test_webhook");
    }

    @Test
    void handle_WhenEventIsDeliveredTwice_DelegatesBothEvents()
            throws Exception {
        String payload = eventPayload("paid");
        String signature = signature(payload);

        webhookService.handle(payload, signature);
        webhookService.handle(payload, signature);

        verify(paymentService, times(2)).markPaid("cs_test_webhook");
    }

    @Test
    void handle_WithUnpaidCheckoutEvent_DoesNotUpdatePayment()
            throws Exception {
        String payload = eventPayload("unpaid");

        webhookService.handle(payload, signature(payload));

        verifyNoInteractions(paymentService);
    }

    @Test
    void handle_WithInvalidSignature_RejectsWebhook() {
        String payload = eventPayload("paid");

        assertThatThrownBy(() ->
                webhookService.handle(payload, "t=1,v1=invalid"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Invalid Stripe webhook signature");
        verifyNoInteractions(paymentService);
    }

    private String eventPayload(String paymentStatus) {
        return """
                {
                  "id": "evt_test_webhook",
                  "object": "event",
                  "api_version": "2026-06-24.dahlia",
                  "created": %d,
                  "data": {
                    "object": {
                      "id": "cs_test_webhook",
                      "object": "checkout.session",
                      "payment_status": "%s"
                    }
                  },
                  "livemode": false,
                  "pending_webhooks": 1,
                  "type": "checkout.session.completed"
                }
                """.formatted(Instant.now().getEpochSecond(), paymentStatus);
    }

    private String signature(String payload) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        ));
        byte[] digest = mac.doFinal(
                (timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)
        );
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
    }
}
