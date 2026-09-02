package com.vdmytriv.carsharing.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.vdmytriv.carsharing.TestcontainersConfiguration;
import com.vdmytriv.carsharing.dto.user.UserRegistrationRequest;
import com.vdmytriv.carsharing.model.Role;
import com.vdmytriv.carsharing.model.RoleName;
import com.vdmytriv.carsharing.model.User;
import com.vdmytriv.carsharing.repository.RoleRepository;
import com.vdmytriv.carsharing.repository.UserRepository;
import com.vdmytriv.carsharing.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "app.bootstrap.manager.enabled=true",
        "app.bootstrap.manager.email=manager@example.com",
        "app.bootstrap.manager.first-name=Fleet",
        "app.bootstrap.manager.last-name=Manager",
        "app.bootstrap.manager.password=SecurePassword123"
})
@ActiveProfiles("test")
class ManagerBootstrapIntegrationTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Test
    void applicationStartup_CreatesManagerFromConfiguration() {
        User manager = userRepository.findByEmail("manager@example.com")
                .orElseThrow();

        assertThat(manager.getRole().getName()).isEqualTo(RoleName.MANAGER);
        assertThat(manager.getFirstName()).isEqualTo("Fleet");
        assertThat(passwordEncoder.matches(
                "SecurePassword123",
                manager.getPassword()
        )).isTrue();
    }

    @Test
    void bootstrapManager_WithExistingCustomer_PromotesAccountOnly() {
        Role customerRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow();
        User customer = new User();
        customer.setEmail("existing@example.com");
        customer.setFirstName("Existing");
        customer.setLastName("Customer");
        customer.setPassword(passwordEncoder.encode("OriginalPassword123"));
        customer.setRole(customerRole);
        userRepository.saveAndFlush(customer);

        userService.bootstrapManager(new UserRegistrationRequest(
                "existing@example.com",
                "Replacement",
                "Manager",
                "ReplacementPassword123"
        ));

        User promoted = userRepository.findByEmail("existing@example.com")
                .orElseThrow();
        assertThat(promoted.getRole().getName()).isEqualTo(RoleName.MANAGER);
        assertThat(promoted.getFirstName()).isEqualTo("Existing");
        assertThat(passwordEncoder.matches(
                "OriginalPassword123",
                promoted.getPassword()
        )).isTrue();
        assertThat(passwordEncoder.matches(
                "ReplacementPassword123",
                promoted.getPassword()
        )).isFalse();
    }
}
