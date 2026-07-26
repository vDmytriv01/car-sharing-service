package com.vdmytriv.carsharing.dto.auth;

public record LoginResponse(
        String token,
        String tokenType
) {
}
