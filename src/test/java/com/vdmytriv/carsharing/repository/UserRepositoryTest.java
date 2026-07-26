package com.vdmytriv.carsharing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vdmytriv.carsharing.TestcontainersConfiguration;
import com.vdmytriv.carsharing.model.Role;
import com.vdmytriv.carsharing.model.RoleName;
import com.vdmytriv.carsharing.model.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByName_WithSeededRoles_ReturnsBothRoles() {
        assertThat(roleRepository.findByName(RoleName.CUSTOMER)).isPresent();
        assertThat(roleRepository.findByName(RoleName.MANAGER)).isPresent();
    }

    @Test
    void findByEmail_WithPersistedUser_ReturnsUserWithRole() {
        Role customerRole = roleRepository.findByName(RoleName.CUSTOMER).orElseThrow();
        User user = new User();
        user.setEmail("customer@example.com");
        user.setFirstName("Test");
        user.setLastName("Customer");
        user.setPassword("encoded-password");
        user.setRole(customerRole);
        userRepository.saveAndFlush(user);
        entityManager.clear();

        User persistedUser = userRepository.findByEmail("customer@example.com").orElseThrow();

        assertThat(persistedUser.getFirstName()).isEqualTo("Test");
        assertThat(persistedUser.getLastName()).isEqualTo("Customer");
        assertThat(persistedUser.getRole().getName()).isEqualTo(RoleName.CUSTOMER);
        assertThat(userRepository.existsByEmail("customer@example.com")).isTrue();
    }
}
