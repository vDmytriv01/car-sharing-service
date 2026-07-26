package com.vdmytriv.carsharing.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vdmytriv.carsharing.TestcontainersConfiguration;
import com.vdmytriv.carsharing.model.Role;
import com.vdmytriv.carsharing.model.RoleName;
import com.vdmytriv.carsharing.model.User;
import com.vdmytriv.carsharing.repository.RoleRepository;
import com.vdmytriv.carsharing.repository.UserRepository;
import com.vdmytriv.carsharing.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
@Transactional
@ActiveProfiles("test")
class UserApiIntegrationTest {

    private static final String CUSTOMER_EMAIL = "customer@example.com";
    private static final String PASSWORD = "Password123";

    @Autowired
    private JwtService jwtService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void registerAndLogin_WithValidRequests_ReturnsUserAndToken() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "new.user@example.com",
                                  "firstName": "New",
                                  "lastName": "User",
                                  "password": "Password123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("new.user@example.com"))
                .andExpect(jsonPath("$.firstName").value("New"))
                .andExpect(jsonPath("$.lastName").value("User"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "new.user@example.com",
                                  "password": "Password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void register_WithDuplicateEmail_ReturnsConflict() throws Exception {
        saveUser(CUSTOMER_EMAIL, RoleName.CUSTOMER);

        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "CUSTOMER@example.com",
                                  "firstName": "Other",
                                  "lastName": "User",
                                  "password": "Password123"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        "User with email customer@example.com already exists"
                ));
    }

    @Test
    void register_WithInvalidRequest_ReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "firstName": "",
                                  "lastName": "U",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.firstName").exists())
                .andExpect(jsonPath("$.fieldErrors.lastName").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void login_WithInvalidCredentials_ReturnsUnauthorized() throws Exception {
        saveUser(CUSTOMER_EMAIL, RoleName.CUSTOMER);

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "customer@example.com",
                                  "password": "WrongPassword123"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void getCurrentUser_WithValidToken_ReturnsProfile() throws Exception {
        User user = saveUser(CUSTOMER_EMAIL, RoleName.CUSTOMER);

        mockMvc.perform(get("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value(CUSTOMER_EMAIL))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void patchCurrentUser_WithPartialRequest_UpdatesProvidedFields() throws Exception {
        User user = saveUser(CUSTOMER_EMAIL, RoleName.CUSTOMER);

        mockMvc.perform(patch("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Updated"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(CUSTOMER_EMAIL))
                .andExpect(jsonPath("$.firstName").value("Updated"))
                .andExpect(jsonPath("$.lastName").value("User"));
    }

    @Test
    void patchCurrentUser_WithBlankName_ReturnsBadRequest() throws Exception {
        User user = saveUser(CUSTOMER_EMAIL, RoleName.CUSTOMER);

        mockMvc.perform(patch("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "  "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.firstName").value("must not be blank"));
    }

    @Test
    void putCurrentUser_WithCompleteRequest_UpdatesProfile() throws Exception {
        User user = saveUser(CUSTOMER_EMAIL, RoleName.CUSTOMER);

        mockMvc.perform(put("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Updated",
                                  "lastName": "Customer"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"))
                .andExpect(jsonPath("$.lastName").value("Customer"));
    }

    @Test
    void updateRole_WithManagerToken_UpdatesTargetUser() throws Exception {
        User manager = saveUser("manager@example.com", RoleName.MANAGER);
        User customer = saveUser(CUSTOMER_EMAIL, RoleName.CUSTOMER);

        mockMvc.perform(put("/users/{id}/role", customer.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "MANAGER"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customer.getId()))
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    void updateRole_WithCustomerToken_ReturnsForbidden() throws Exception {
        User customer = saveUser(CUSTOMER_EMAIL, RoleName.CUSTOMER);

        mockMvc.perform(put("/users/{id}/role", customer.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "MANAGER"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user.getEmail());
    }

    private User saveUser(String email, RoleName roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User();
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }
}
