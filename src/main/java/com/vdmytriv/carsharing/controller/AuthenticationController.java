package com.vdmytriv.carsharing.controller;

import com.vdmytriv.carsharing.dto.auth.LoginRequest;
import com.vdmytriv.carsharing.dto.auth.LoginResponse;
import com.vdmytriv.carsharing.dto.user.UserRegistrationRequest;
import com.vdmytriv.carsharing.dto.user.UserResponse;
import com.vdmytriv.carsharing.service.AuthenticationService;
import com.vdmytriv.carsharing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "User registration and login")
@RestController
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final UserService userService;

    @Operation(summary = "Register a customer account")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@RequestBody @Valid UserRegistrationRequest request) {
        return userService.register(request);
    }

    @Operation(summary = "Authenticate and receive a JWT")
    @PostMapping("/login")
    public LoginResponse login(@RequestBody @Valid LoginRequest request) {
        return authenticationService.login(request);
    }
}
