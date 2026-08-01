package com.vdmytriv.carsharing.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "notification.telegram")
public record TelegramProperties(
        @NotNull URI apiUrl,
        @NotBlank String botToken,
        @NotBlank String chatId
) {

    @AssertTrue(message = "Telegram API URL must be an absolute HTTPS URL")
    public boolean isApiUrlValid() {
        return apiUrl == null
                || apiUrl.isAbsolute()
                && apiUrl.getHost() != null
                && "https".equalsIgnoreCase(apiUrl.getScheme());
    }
}
