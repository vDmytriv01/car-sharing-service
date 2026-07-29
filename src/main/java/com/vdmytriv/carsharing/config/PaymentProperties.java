package com.vdmytriv.carsharing.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "payment")
public record PaymentProperties(
        @NotNull URI baseUrl,
        @NotBlank String currency,
        @NotNull @DecimalMin("1.0") BigDecimal fineMultiplier
) {

    @AssertTrue(message = "Payment base URL must be an absolute HTTP or HTTPS URL")
    public boolean isBaseUrlValid() {
        if (baseUrl == null) {
            return true;
        }
        String scheme = baseUrl.getScheme();
        return baseUrl.isAbsolute()
                && baseUrl.getHost() != null
                && ("http".equalsIgnoreCase(scheme)
                || "https".equalsIgnoreCase(scheme));
    }
}
