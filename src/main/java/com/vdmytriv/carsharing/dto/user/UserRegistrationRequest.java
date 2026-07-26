package com.vdmytriv.carsharing.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRegistrationRequest(
        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 2, max = 100)
        String firstName,

        @NotBlank
        @Size(min = 2, max = 100)
        String lastName,

        @NotBlank
        @Size(min = 8, max = 72)
        String password
) {
}
