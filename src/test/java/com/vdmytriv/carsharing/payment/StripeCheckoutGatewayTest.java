package com.vdmytriv.carsharing.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.service.CheckoutService;
import com.stripe.service.V1Services;
import com.stripe.service.checkout.SessionService;
import com.vdmytriv.carsharing.exception.PaymentProviderException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StripeCheckoutGatewayTest {

    @Mock
    private StripeClient stripeClient;
    @Mock
    private V1Services v1Services;
    @Mock
    private CheckoutService checkoutService;
    @Mock
    private SessionService sessionService;

    private StripeCheckoutGateway gateway;

    @BeforeEach
    void setUp() {
        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.checkout()).thenReturn(checkoutService);
        when(checkoutService.sessions()).thenReturn(sessionService);
        gateway = new StripeCheckoutGateway(stripeClient);
    }

    @Test
    void create_WithValidRequest_MapsCheckoutParametersAndReturnsSession()
            throws StripeException {
        Session stripeSession = new Session();
        stripeSession.setId("cs_test_gateway");
        stripeSession.setUrl(
                "https://checkout.stripe.com/c/pay/cs_test_gateway"
        );
        when(sessionService.create(any(SessionCreateParams.class)))
                .thenReturn(stripeSession);
        CheckoutSessionRequest request = request();

        CheckoutSessionResult result = gateway.create(request);

        assertThat(result.sessionId()).isEqualTo("cs_test_gateway");
        assertThat(result.sessionUrl()).isEqualTo(stripeSession.getUrl());

        ArgumentCaptor<SessionCreateParams> captor =
                ArgumentCaptor.forClass(SessionCreateParams.class);
        verify(sessionService).create(captor.capture());
        SessionCreateParams params = captor.getValue();

        assertThat(params.getMode()).isEqualTo(SessionCreateParams.Mode.PAYMENT);
        assertThat(params.getCustomerEmail()).isEqualTo(request.customerEmail());
        assertThat(params.getSuccessUrl()).isEqualTo(request.successUrl());
        assertThat(params.getCancelUrl()).isEqualTo(request.cancelUrl());
        assertThat(params.getMetadata()).isEqualTo(request.metadata());

        SessionCreateParams.LineItem lineItem =
                params.getLineItems().getFirst();
        assertThat(lineItem.getQuantity()).isEqualTo(1L);
        assertThat(lineItem.getPriceData().getCurrency()).isEqualTo("usd");
        assertThat(lineItem.getPriceData().getUnitAmount()).isEqualTo(14997L);
        assertThat(lineItem.getPriceData().getProductData().getName())
                .isEqualTo("Car rental payment #17");
    }

    @Test
    void create_WhenStripeReturnsSessionWithoutUrl_ThrowsProviderException()
            throws StripeException {
        Session incompleteSession = new Session();
        incompleteSession.setId("cs_test_incomplete");
        when(sessionService.create(any(SessionCreateParams.class)))
                .thenReturn(incompleteSession);

        assertThatThrownBy(() -> gateway.create(request()))
                .isInstanceOf(PaymentProviderException.class)
                .hasMessage("Stripe returned an incomplete checkout session");
    }

    @Test
    void isPaid_WhenStripeSessionIsPaid_ReturnsTrue() throws StripeException {
        Session session = new Session();
        session.setPaymentStatus("paid");
        when(sessionService.retrieve("cs_test_paid")).thenReturn(session);

        assertThat(gateway.isPaid("cs_test_paid")).isTrue();
    }

    @Test
    void isPaid_WhenStripeSessionIsUnpaid_ReturnsFalse() throws StripeException {
        Session session = new Session();
        session.setPaymentStatus("unpaid");
        when(sessionService.retrieve("cs_test_unpaid")).thenReturn(session);

        assertThat(gateway.isPaid("cs_test_unpaid")).isFalse();
    }

    private CheckoutSessionRequest request() {
        return new CheckoutSessionRequest(
                14997L,
                "usd",
                1L,
                "Car rental payment #17",
                "customer@example.com",
                "https://example.com/payments/success"
                        + "?session_id={CHECKOUT_SESSION_ID}",
                "https://example.com/payments/cancel",
                Map.of(
                        "rentalId", "17",
                        "paymentType", "PAYMENT"
                )
        );
    }
}
