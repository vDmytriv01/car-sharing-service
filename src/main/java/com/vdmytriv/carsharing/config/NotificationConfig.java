package com.vdmytriv.carsharing.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(TelegramProperties.class)
public class NotificationConfig {

    @Bean
    public Clock clock(
            @Value("${notification.overdue-rentals.zone}") String zone
    ) {
        return Clock.system(ZoneId.of(zone));
    }

    @Bean
    public RestClient telegramRestClient(
            TelegramProperties properties
    ) {
        return RestClient.builder()
                .baseUrl(properties.apiUrl()
                        + "/bot"
                        + properties.botToken())
                .build();
    }
}
