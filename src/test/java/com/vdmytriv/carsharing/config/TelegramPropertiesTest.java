package com.vdmytriv.carsharing.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class TelegramPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfig.class)
                    .withPropertyValues(
                            "notification.telegram.bot-token=123456:test-token",
                            "notification.telegram.chat-id=-1001234567890"
                    );

    @Test
    void telegramProperties_WithValidValues_BindsConfiguration() {
        contextRunner
                .withPropertyValues(
                        "notification.telegram.api-url="
                                + "https://api.telegram.org"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    TelegramProperties properties =
                            context.getBean(TelegramProperties.class);
                    assertThat(properties.apiUrl())
                            .isEqualTo(URI.create(
                                    "https://api.telegram.org"
                            ));
                    assertThat(properties.chatId())
                            .isEqualTo("-1001234567890");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "api.telegram.org",
            "http://api.telegram.org"
    })
    void telegramProperties_WithInvalidApiUrl_FailsStartup(String apiUrl) {
        contextRunner
                .withPropertyValues(
                        "notification.telegram.api-url=" + apiUrl
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining(
                                    "Telegram API URL must be an absolute HTTPS"
                            );
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TelegramProperties.class)
    static class TestConfig {
    }
}
