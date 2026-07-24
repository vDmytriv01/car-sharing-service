package com.vdmytriv.carsharing.dto.user;

import com.vdmytriv.carsharing.model.RoleName;
import jakarta.validation.constraints.NotNull;

public record UserRoleUpdateRequest(
        @NotNull
        RoleName role
) {
}
