package com.vdmytriv.carsharing.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vdmytriv.carsharing.TestcontainersConfiguration;
import com.vdmytriv.carsharing.model.Car;
import com.vdmytriv.carsharing.model.CarType;
import com.vdmytriv.carsharing.model.Payment;
import com.vdmytriv.carsharing.model.PaymentStatus;
import com.vdmytriv.carsharing.model.PaymentType;
import com.vdmytriv.carsharing.model.Rental;
import com.vdmytriv.carsharing.model.Role;
import com.vdmytriv.carsharing.model.RoleName;
import com.vdmytriv.carsharing.model.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@ActiveProfiles("test")
class PaymentRepositoryTest {

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RentalRepository rentalRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void save_WithValidPayment_PersistsStripeSessionAndRental() {
        Rental rental = saveRental();
        Payment payment = createPayment(
                rental,
                "cs_test_valid",
                new BigDecimal("149.97")
        );

        Payment savedPayment = paymentRepository.saveAndFlush(payment);
        entityManager.clear();

        Payment persistedPayment = paymentRepository
                .findBySessionId("cs_test_valid")
                .orElseThrow();
        assertThat(persistedPayment.getId()).isEqualTo(savedPayment.getId());
        assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(persistedPayment.getType()).isEqualTo(PaymentType.PAYMENT);
        assertThat(persistedPayment.getRental().getId()).isEqualTo(rental.getId());
        assertThat(persistedPayment.getSessionUrl())
                .isEqualTo("https://checkout.stripe.com/c/pay/cs_test_valid");
        assertThat(persistedPayment.getAmountToPay())
                .isEqualByComparingTo("149.97");
    }

    @Test
    void save_WithDuplicateSessionId_IsRejectedByDatabase() {
        Rental rental = saveRental();
        paymentRepository.saveAndFlush(createPayment(
                rental,
                "cs_test_duplicate",
                new BigDecimal("49.99")
        ));

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(createPayment(
                rental,
                "cs_test_duplicate",
                new BigDecimal("49.99")
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @MethodSource("nonPositiveAmounts")
    void save_WithNonPositiveAmount_IsRejectedByDatabase(BigDecimal amount) {
        Payment payment = createPayment(
                saveRental(),
                "cs_test_invalid_amount",
                amount
        );

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(payment))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Stream<Arguments> nonPositiveAmounts() {
        return Stream.of(
                Arguments.of(new BigDecimal("0.00")),
                Arguments.of(new BigDecimal("-0.01"))
        );
    }

    private Payment createPayment(
            Rental rental,
            String sessionId,
            BigDecimal amount
    ) {
        Payment payment = new Payment();
        payment.setStatus(PaymentStatus.PENDING);
        payment.setType(PaymentType.PAYMENT);
        payment.setRental(rental);
        payment.setSessionUrl(
                "https://checkout.stripe.com/c/pay/" + sessionId
        );
        payment.setSessionId(sessionId);
        payment.setAmountToPay(amount);
        return payment;
    }

    private Rental saveRental() {
        User user = saveUser();
        Car car = saveCar();
        Rental rental = new Rental();
        rental.setRentalDate(LocalDate.of(2026, 7, 29));
        rental.setReturnDate(LocalDate.of(2026, 8, 1));
        rental.setCar(car);
        rental.setUser(user);
        return rentalRepository.saveAndFlush(rental);
    }

    private Car saveCar() {
        Car car = new Car();
        car.setModel("Octavia");
        car.setBrand("Skoda");
        car.setType(CarType.SEDAN);
        car.setInventory(2);
        car.setDailyFee(new BigDecimal("49.99"));
        return carRepository.saveAndFlush(car);
    }

    private User saveUser() {
        Role customerRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow();
        User user = new User();
        user.setEmail("customer@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPassword("encoded-password");
        user.setRole(customerRole);
        return userRepository.saveAndFlush(user);
    }
}
