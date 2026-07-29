package com.vdmytriv.carsharing.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class PaymentPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfig.class)
                    .withPropertyValues(
                            "payment.currency=usd",
                            "payment.fine-multiplier=1.50"
                    );

    @Test
    void paymentProperties_WithValidBaseUrl_BindsConfiguration() {
        contextRunner
                .withPropertyValues(
                        "payment.base-url=https://car-sharing.example"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(PaymentProperties.class).baseUrl())
                            .isEqualTo(URI.create(
                                    "https://car-sharing.example"
                            ));
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "localhost:8080",
            "ftp://car-sharing.example"
    })
    void paymentProperties_WithInvalidBaseUrl_FailsStartup(String baseUrl) {
        contextRunner
                .withPropertyValues("payment.base-url=" + baseUrl)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining(
                                    "Payment base URL must be an absolute HTTP"
                            );
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PaymentProperties.class)
    static class TestConfig {
    }
}
