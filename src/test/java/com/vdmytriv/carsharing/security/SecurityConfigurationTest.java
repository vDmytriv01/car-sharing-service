package com.vdmytriv.carsharing.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vdmytriv.carsharing.TestcontainersConfiguration;
import com.vdmytriv.carsharing.model.Role;
import com.vdmytriv.carsharing.model.RoleName;
import com.vdmytriv.carsharing.model.User;
import com.vdmytriv.carsharing.repository.RoleRepository;
import com.vdmytriv.carsharing.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Import({
        TestcontainersConfiguration.class,
        SecurityConfigurationTest.TestSecurityController.class
})
@AutoConfigureMockMvc
@SpringBootTest
@Transactional
@ActiveProfiles("test")
class SecurityConfigurationTest {

    private static final String EMAIL = "customer@example.com";

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    private String token;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName(RoleName.CUSTOMER).orElseThrow();
        saveUser(EMAIL, customerRole);
        token = jwtService.generateToken(EMAIL);
    }

    @Test
    void getSecuredEndpoint_WithoutToken_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/test/secured"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/test/secured"));
    }

    @Test
    void getSecuredEndpoint_WithMalformedToken_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/test/secured")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer malformed-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/test/secured"));
    }

    @Test
    void getSecuredEndpoint_WithValidToken_ReturnsContent() throws Exception {
        mockMvc.perform(get("/test/secured")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(content().string("secured"));
    }

    @Test
    void getManagerEndpoint_WithCustomerToken_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/test/manager")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.path").value("/test/manager"));
    }

    @Test
    void getManagerEndpoint_WithManagerToken_ReturnsContent() throws Exception {
        Role managerRole = roleRepository.findByName(RoleName.MANAGER).orElseThrow();
        String managerEmail = "manager@example.com";
        saveUser(managerEmail, managerRole);
        String managerToken = jwtService.generateToken(managerEmail);

        mockMvc.perform(get("/test/manager")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerToken)))
                .andExpect(status().isOk())
                .andExpect(content().string("manager"));
    }

    @Test
    void getSecuredEndpoint_WithExpiredToken_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/test/secured")
                        .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/test/secured"));
    }

    @Test
    void getSecuredEndpoint_WithUnknownUserToken_ReturnsUnauthorized() throws Exception {
        String unknownUserToken = jwtService.generateToken("unknown@example.com");

        mockMvc.perform(get("/test/secured")
                        .header(HttpHeaders.AUTHORIZATION, bearer(unknownUserToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/test/secured"));
    }

    private String bearer(String jwt) {
        return "Bearer " + jwt;
    }

    private String expiredToken() {
        SecretKey signingKey = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(jwtProperties.secret())
        );
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(EMAIL)
                .issuer(jwtProperties.issuer())
                .issuedAt(Date.from(now.minus(Duration.ofHours(2))))
                .expiration(Date.from(now.minus(Duration.ofHours(1))))
                .signWith(signingKey)
                .compact();
    }

    private void saveUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPassword("encoded-password");
        user.setRole(role);
        userRepository.saveAndFlush(user);
    }

    @RestController
    static class TestSecurityController {

        @GetMapping("/test/secured")
        String secured() {
            return "secured";
        }

        @PreAuthorize("hasRole('MANAGER')")
        @GetMapping("/test/manager")
        String manager() {
            return "manager";
        }
    }
}
