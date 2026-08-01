package com.vdmytriv.carsharing.notification;

import com.vdmytriv.carsharing.config.TelegramProperties;
import com.vdmytriv.carsharing.exception.NotificationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class TelegramNotificationService implements NotificationService {

    private final RestClient restClient;
    private final TelegramProperties properties;

    public TelegramNotificationService(
            @Qualifier("telegramRestClient") RestClient restClient,
            TelegramProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public void send(String message) {
        TelegramResponse response;
        try {
            response = restClient.post()
                    .uri("/sendMessage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TelegramMessageRequest(
                            properties.chatId(),
                            message
                    ))
                    .retrieve()
                    .body(TelegramResponse.class);
        } catch (RestClientException exception) {
            throw new NotificationException(
                    "Could not send Telegram notification",
                    exception
            );
        }

        if (response == null || !response.ok()) {
            String description = response == null
                    ? "empty response"
                    : response.description();
            throw new NotificationException(
                    "Telegram rejected notification: " + description
            );
        }
    }
}
