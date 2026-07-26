package com.vdmytriv.carsharing.dto.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserPatchRequest(
        @Pattern(regexp = ".*\\S.*", message = "must not be blank")
        @Size(min = 2, max = 100)
        String firstName,

        @Pattern(regexp = ".*\\S.*", message = "must not be blank")
        @Size(min = 2, max = 100)
        String lastName
) {
}
