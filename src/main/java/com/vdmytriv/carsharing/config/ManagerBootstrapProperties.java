package com.vdmytriv.carsharing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap.manager")
public record ManagerBootstrapProperties(
        boolean enabled,
        String email,
        String firstName,
        String lastName,
        String password
) {
}
