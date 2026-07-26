package com.vdmytriv.carsharing.dto.user;

import com.vdmytriv.carsharing.model.RoleName;

public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        RoleName role
) {
}
