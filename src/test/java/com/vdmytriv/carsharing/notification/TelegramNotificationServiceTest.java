package com.vdmytriv.carsharing.notification;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.vdmytriv.carsharing.config.TelegramProperties;
import com.vdmytriv.carsharing.exception.NotificationException;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TelegramNotificationServiceTest {

    private static final String SEND_MESSAGE_URL =
            "https://api.telegram.org/bot123456:test-token/sendMessage";

    private MockRestServiceServer server;
    private TelegramNotificationService notificationService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        TelegramProperties properties = new TelegramProperties(
                URI.create("https://api.telegram.org"),
                "123456:test-token",
                "-1001234567890"
        );
        notificationService = new TelegramNotificationService(
                builder.baseUrl(
                        properties.apiUrl()
                                + "/bot"
                                + properties.botToken()
                ).build(),
                properties
        );
    }

    @Test
    void send_WhenMessageIsValid_CallsTelegramApi() {
        server.expect(once(), requestTo(SEND_MESSAGE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "chat_id": "-1001234567890",
                          "text": "New rental #42"
                        }
                        """))
                .andRespond(withSuccess(
                        """
                                {
                                  "ok": true
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        notificationService.send("New rental #42");

        server.verify();
    }

    @Test
    void send_WhenTelegramRejectsMessage_ThrowsNotificationException() {
        server.expect(once(), requestTo(SEND_MESSAGE_URL))
                .andRespond(withSuccess(
                        """
                                {
                                  "ok": false,
                                  "description": "Bad Request: chat not found"
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() ->
                notificationService.send("New rental #42"))
                .isInstanceOf(NotificationException.class)
                .hasMessage(
                        "Telegram rejected notification: "
                                + "Bad Request: chat not found"
                );

        server.verify();
    }
}
