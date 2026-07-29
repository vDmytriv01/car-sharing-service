package com.vdmytriv.carsharing.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vdmytriv.carsharing.TestcontainersConfiguration;
import com.vdmytriv.carsharing.exception.PaymentProviderException;
import com.vdmytriv.carsharing.model.Car;
import com.vdmytriv.carsharing.model.CarType;
import com.vdmytriv.carsharing.model.Payment;
import com.vdmytriv.carsharing.model.PaymentStatus;
import com.vdmytriv.carsharing.model.PaymentType;
import com.vdmytriv.carsharing.model.Rental;
import com.vdmytriv.carsharing.model.Role;
import com.vdmytriv.carsharing.model.RoleName;
import com.vdmytriv.carsharing.model.User;
import com.vdmytriv.carsharing.payment.CheckoutGateway;
import com.vdmytriv.carsharing.payment.CheckoutSessionRequest;
import com.vdmytriv.carsharing.payment.CheckoutSessionResult;
import com.vdmytriv.carsharing.repository.CarRepository;
import com.vdmytriv.carsharing.repository.PaymentRepository;
import com.vdmytriv.carsharing.repository.RentalRepository;
import com.vdmytriv.carsharing.repository.RoleRepository;
import com.vdmytriv.carsharing.repository.UserRepository;
import com.vdmytriv.carsharing.security.JwtService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
@Transactional
@ActiveProfiles("test")
class PaymentApiIntegrationTest {

    @Autowired
    private CarRepository carRepository;

    @MockitoBean
    private CheckoutGateway checkoutGateway;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RentalRepository rentalRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createPayment_WithValidRequest_ReturnsPendingPayment()
            throws Exception {
        User customer = saveUser("customer@example.com", RoleName.CUSTOMER);
        Rental rental = saveRental(customer);
        when(checkoutGateway.create(any())).thenReturn(
                new CheckoutSessionResult(
                        "cs_test_api",
                        "https://checkout.stripe.com/c/pay/cs_test_api"
                )
        );

        mockMvc.perform(post("/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .with(request -> {
                            request.setServerName("untrusted.example");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rentalId": %d,
                                  "type": "PAYMENT"
                                }
                                """.formatted(rental.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.type").value("PAYMENT"))
                .andExpect(jsonPath("$.rentalId").value(rental.getId()))
                .andExpect(jsonPath("$.userId").value(customer.getId()))
                .andExpect(jsonPath("$.sessionId").value("cs_test_api"))
                .andExpect(jsonPath("$.sessionUrl")
                        .value("https://checkout.stripe.com/c/pay/cs_test_api"))
                .andExpect(jsonPath("$.amountToPay").value(150.00));

        ArgumentCaptor<CheckoutSessionRequest> requestCaptor =
                ArgumentCaptor.forClass(CheckoutSessionRequest.class);
        verify(checkoutGateway).create(requestCaptor.capture());
        assertThat(requestCaptor.getValue().successUrl())
                .startsWith("http://localhost:8080/payments/success");
        assertThat(requestCaptor.getValue().cancelUrl())
                .isEqualTo("http://localhost:8080/payments/cancel");
    }

    @Test
    void createPayment_WhenStripeIsUnavailable_ReturnsBadGateway()
            throws Exception {
        User customer = saveUser("customer@example.com", RoleName.CUSTOMER);
        Rental rental = saveRental(customer);
        when(checkoutGateway.create(any())).thenThrow(
                new PaymentProviderException("Could not create Stripe checkout session")
        );

        mockMvc.perform(post("/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rentalId": %d,
                                  "type": "PAYMENT"
                                }
                                """.formatted(rental.getId())))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.error").value("Bad Gateway"))
                .andExpect(jsonPath("$.message")
                        .value("Could not create Stripe checkout session"))
                .andExpect(jsonPath("$.path").value("/payments"));
    }

    @Test
    void getPayments_AsCustomer_ReturnsOnlyOwnPayments() throws Exception {
        User customer = saveUser("customer@example.com", RoleName.CUSTOMER);
        User anotherCustomer = saveUser(
                "another@example.com",
                RoleName.CUSTOMER
        );
        Payment ownPayment = savePayment(
                saveRental(customer),
                "cs_test_own"
        );
        savePayment(saveRental(anotherCustomer), "cs_test_another");

        mockMvc.perform(get("/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id")
                        .value(ownPayment.getId()))
                .andExpect(jsonPath("$.content[0].userId")
                        .value(customer.getId()));
    }

    @Test
    void getPayments_AsManager_FiltersByUser() throws Exception {
        User manager = saveUser("manager@example.com", RoleName.MANAGER);
        User firstCustomer = saveUser(
                "first@example.com",
                RoleName.CUSTOMER
        );
        User secondCustomer = saveUser(
                "second@example.com",
                RoleName.CUSTOMER
        );
        savePayment(saveRental(firstCustomer), "cs_test_first");
        Payment secondPayment = savePayment(
                saveRental(secondCustomer),
                "cs_test_second"
        );

        mockMvc.perform(get("/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .param("user_id", secondCustomer.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id")
                        .value(secondPayment.getId()))
                .andExpect(jsonPath("$.content[0].userId")
                        .value(secondCustomer.getId()));
    }

    @Test
    void getPayments_AsCustomer_IgnoresRequestedUserId() throws Exception {
        User customer = saveUser("customer@example.com", RoleName.CUSTOMER);
        User anotherCustomer = saveUser(
                "another@example.com",
                RoleName.CUSTOMER
        );
        Payment ownPayment = savePayment(
                saveRental(customer),
                "cs_test_own"
        );
        savePayment(saveRental(anotherCustomer), "cs_test_another");

        mockMvc.perform(get("/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .param("user_id", anotherCustomer.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id")
                        .value(ownPayment.getId()));
    }

    @Test
    void createPayment_ForAnotherCustomersRental_ReturnsNotFound()
            throws Exception {
        User owner = saveUser("owner@example.com", RoleName.CUSTOMER);
        User anotherCustomer = saveUser(
                "another@example.com",
                RoleName.CUSTOMER
        );
        Rental rental = saveRental(owner);

        mockMvc.perform(post("/payments")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearer(anotherCustomer)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rentalId": %d,
                                  "type": "PAYMENT"
                                }
                                """.formatted(rental.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Rental not found: " + rental.getId()));
    }

    @Test
    void createPayment_WithMissingFields_ReturnsValidationErrors()
            throws Exception {
        User customer = saveUser("customer@example.com", RoleName.CUSTOMER);

        mockMvc.perform(post("/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.rentalId").exists())
                .andExpect(jsonPath("$.fieldErrors.type").exists());
    }

    @Test
    void payments_WithoutAuthentication_ReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/payments"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rentalId": 1,
                                  "type": "PAYMENT"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user.getEmail());
    }

    private Payment savePayment(Rental rental, String sessionId) {
        Payment payment = new Payment();
        payment.setStatus(PaymentStatus.PENDING);
        payment.setType(PaymentType.PAYMENT);
        payment.setRental(rental);
        payment.setSessionId(sessionId);
        payment.setSessionUrl(
                "https://checkout.stripe.com/c/pay/" + sessionId
        );
        payment.setAmountToPay(new BigDecimal("150.00"));
        return paymentRepository.saveAndFlush(payment);
    }

    private Rental saveRental(User user) {
        Car car = new Car();
        car.setModel("Corolla");
        car.setBrand("Toyota");
        car.setType(CarType.SEDAN);
        car.setInventory(1);
        car.setDailyFee(new BigDecimal("50.00"));
        Car savedCar = carRepository.saveAndFlush(car);

        Rental rental = new Rental();
        rental.setRentalDate(LocalDate.now());
        rental.setReturnDate(LocalDate.now().plusDays(3));
        rental.setCar(savedCar);
        rental.setUser(user);
        return rentalRepository.saveAndFlush(rental);
    }

    private User saveUser(String email, RoleName roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User();
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPassword(passwordEncoder.encode("Password123"));
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }
}
