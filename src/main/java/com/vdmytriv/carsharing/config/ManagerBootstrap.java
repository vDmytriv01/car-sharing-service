package com.vdmytriv.carsharing.config;

import com.vdmytriv.carsharing.dto.user.UserRegistrationRequest;
import com.vdmytriv.carsharing.service.UserService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ManagerBootstrap implements CommandLineRunner {

    private final ManagerBootstrapProperties properties;
    private final UserService userService;
    private final Validator validator;

    @Override
    public void run(String... args) {
        if (!properties.enabled()) {
            return;
        }
        UserRegistrationRequest request = new UserRegistrationRequest(
                properties.email(),
                properties.firstName(),
                properties.lastName(),
                properties.password()
        );
        Set<ConstraintViolation<UserRegistrationRequest>> violations =
                validator.validate(request);
        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "Invalid manager bootstrap configuration"
            );
        }
        userService.bootstrapManager(request);
    }
}
