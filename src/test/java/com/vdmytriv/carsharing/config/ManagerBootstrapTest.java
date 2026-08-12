package com.vdmytriv.carsharing.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.vdmytriv.carsharing.dto.user.UserRegistrationRequest;
import com.vdmytriv.carsharing.service.UserService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManagerBootstrapTest {

    @Mock
    private UserService userService;

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void run_WhenDisabled_DoesNotCreateManager() {
        ManagerBootstrap bootstrap = new ManagerBootstrap(
                new ManagerBootstrapProperties(
                        false,
                        null,
                        null,
                        null,
                        null
                ),
                userService,
                validator
        );

        bootstrap.run();

        verify(userService, never()).bootstrapManager(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void run_WhenEnabled_CreatesConfiguredManager() {
        ManagerBootstrap bootstrap = new ManagerBootstrap(
                new ManagerBootstrapProperties(
                        true,
                        "manager@example.com",
                        "Fleet",
                        "Manager",
                        "SecurePassword123"
                ),
                userService,
                validator
        );

        bootstrap.run();

        verify(userService).bootstrapManager(new UserRegistrationRequest(
                "manager@example.com",
                "Fleet",
                "Manager",
                "SecurePassword123"
        ));
    }

    @Test
    void run_WhenEnabledWithInvalidConfiguration_FailsFast() {
        ManagerBootstrap bootstrap = new ManagerBootstrap(
                new ManagerBootstrapProperties(
                        true,
                        "not-an-email",
                        "F",
                        "Manager",
                        "short"
                ),
                userService,
                validator
        );

        assertThatThrownBy(bootstrap::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("manager bootstrap configuration");
        verify(userService, never()).bootstrapManager(
                org.mockito.ArgumentMatchers.any()
        );
    }
}
