package com.vdmytriv.carsharing.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.vdmytriv.carsharing.TestcontainersConfiguration;
import com.vdmytriv.carsharing.dto.payment.PaymentResponse;
import com.vdmytriv.carsharing.model.Car;
import com.vdmytriv.carsharing.model.CarType;
import com.vdmytriv.carsharing.model.PaymentType;
import com.vdmytriv.carsharing.model.Rental;
import com.vdmytriv.carsharing.model.Role;
import com.vdmytriv.carsharing.model.RoleName;
import com.vdmytriv.carsharing.model.User;
import com.vdmytriv.carsharing.payment.CheckoutGateway;
import com.vdmytriv.carsharing.payment.CheckoutSessionResult;
import com.vdmytriv.carsharing.repository.CarRepository;
import com.vdmytriv.carsharing.repository.PaymentRepository;
import com.vdmytriv.carsharing.repository.RentalRepository;
import com.vdmytriv.carsharing.repository.RoleRepository;
import com.vdmytriv.carsharing.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class PaymentConcurrencyIntegrationTest {

    @Autowired
    private CarRepository carRepository;

    @MockitoBean
    private CheckoutGateway checkoutGateway;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private RentalRepository rentalRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        paymentRepository.deleteAllInBatch();
        rentalRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        carRepository.deleteAllInBatch();
    }

    @Test
    void createSession_Concurrently_PersistsOnePayment() throws Exception {
        User customer = saveUser();
        Rental rental = saveRental(customer);
        when(checkoutGateway.create(any())).thenReturn(
                new CheckoutSessionResult(
                        "cs_test_concurrent",
                        "https://checkout.stripe.com/c/pay/cs_test_concurrent"
                )
        );

        List<PaymentResponse> responses = createConcurrently(
                customer.getEmail(),
                rental.getId()
        );

        assertThat(responses).extracting(PaymentResponse::id)
                .containsOnly(responses.getFirst().id());
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    private List<PaymentResponse> createConcurrently(
            String email,
            Long rentalId
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<PaymentResponse> task = () -> {
                ready.countDown();
                start.await();
                return paymentService.createSession(
                        email,
                        rentalId,
                        PaymentType.PAYMENT
                );
            };
            Future<PaymentResponse> first = executor.submit(task);
            Future<PaymentResponse> second = executor.submit(task);
            assertTrue(ready.await(5, SECONDS));
            start.countDown();
            return List.of(
                    first.get(10, SECONDS),
                    second.get(10, SECONDS)
            );
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private Rental saveRental(User user) {
        Car car = new Car();
        car.setModel("Corolla");
        car.setBrand("Toyota");
        car.setType(CarType.SEDAN);
        car.setInventory(1);
        car.setDailyFee(new BigDecimal("50.00"));
        carRepository.saveAndFlush(car);

        Rental rental = new Rental();
        rental.setRentalDate(LocalDate.now());
        rental.setReturnDate(LocalDate.now().plusDays(3));
        rental.setCar(car);
        rental.setUser(user);
        return rentalRepository.saveAndFlush(rental);
    }

    private User saveUser() {
        Role customerRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow();
        User user = new User();
        user.setEmail("customer@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPassword(passwordEncoder.encode("Password123"));
        user.setRole(customerRole);
        return userRepository.saveAndFlush(user);
    }
}
