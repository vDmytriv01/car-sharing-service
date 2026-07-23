package com.vdmytriv.carsharing.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Car Sharing Service API",
                version = "v1",
                description = "REST API for managing cars, rentals, users, and payments"
        )
)
public class OpenApiConfig {
}
