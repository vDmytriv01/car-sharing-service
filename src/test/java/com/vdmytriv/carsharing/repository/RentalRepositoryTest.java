package com.vdmytriv.carsharing.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vdmytriv.carsharing.TestcontainersConfiguration;
import com.vdmytriv.carsharing.model.Car;
import com.vdmytriv.carsharing.model.CarType;
import com.vdmytriv.carsharing.model.Rental;
import com.vdmytriv.carsharing.model.Role;
import com.vdmytriv.carsharing.model.RoleName;
import com.vdmytriv.carsharing.model.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
class RentalRepositoryTest {

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RentalRepository rentalRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void save_WithValidRental_PersistsDatesAndRelationships() {
        User user = saveUser("customer@example.com");
        Car car = saveCar();
        Rental rental = createRental(user, car);

        Rental savedRental = rentalRepository.saveAndFlush(rental);
        entityManager.clear();

        Rental persistedRental = rentalRepository.findById(savedRental.getId()).orElseThrow();
        assertThat(persistedRental.getRentalDate()).isEqualTo(LocalDate.of(2026, 7, 27));
        assertThat(persistedRental.getReturnDate()).isEqualTo(LocalDate.of(2026, 7, 30));
        assertThat(persistedRental.getActualReturnDate()).isNull();
        assertThat(persistedRental.getUser().getId()).isEqualTo(user.getId());
        assertThat(persistedRental.getCar().getId()).isEqualTo(car.getId());
    }

    @Test
    void findByIdAndUserId_WithDifferentUsers_OnlyReturnsOwnersRental() {
        User owner = saveUser("owner@example.com");
        User otherUser = saveUser("other@example.com");
        Rental rental = rentalRepository.saveAndFlush(createRental(owner, saveCar()));

        assertThat(rentalRepository.findByIdAndUserId(rental.getId(), owner.getId()))
                .isPresent();
        assertThat(rentalRepository.findByIdAndUserId(rental.getId(), otherUser.getId()))
                .isEmpty();
    }

    @Test
    void findOverdueRentals_ReturnsOnlyUnreturnedPastDueRentals() {
        User user = saveUser("customer@example.com");
        Car car = saveCar();
        LocalDate today = LocalDate.of(2026, 8, 1);
        Rental overdueRental = createRental(user, car);
        overdueRental.setReturnDate(today.minusDays(1));
        Rental dueToday = createRental(user, car);
        dueToday.setReturnDate(today);
        Rental futureRental = createRental(user, car);
        futureRental.setReturnDate(today.plusDays(1));
        Rental returnedRental = createRental(user, car);
        returnedRental.setReturnDate(today.minusDays(1));
        returnedRental.setActualReturnDate(today.minusDays(1));
        rentalRepository.saveAllAndFlush(List.of(
                overdueRental,
                dueToday,
                futureRental,
                returnedRental
        ));

        List<Rental> rentals = rentalRepository
                .findAllByActualReturnDateIsNullAndReturnDateLessThan(
                        today
                );

        assertThat(rentals)
                .extracting(Rental::getId)
                .containsExactly(overdueRental.getId());
    }

    @ParameterizedTest
    @MethodSource("invalidDateRanges")
    void save_WithInvalidDateRange_IsRejectedByDatabase(
            LocalDate rentalDate,
            LocalDate returnDate,
            LocalDate actualReturnDate
    ) {
        Rental rental = createRental(
                saveUser("customer@example.com"),
                saveCar()
        );
        rental.setRentalDate(rentalDate);
        rental.setReturnDate(returnDate);
        rental.setActualReturnDate(actualReturnDate);

        assertThatThrownBy(() -> rentalRepository.saveAndFlush(rental))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Stream<Arguments> invalidDateRanges() {
        LocalDate rentalDate = LocalDate.of(2026, 7, 27);
        return Stream.of(
                Arguments.of(
                        rentalDate,
                        rentalDate.minusDays(1),
                        null
                ),
                Arguments.of(
                        rentalDate,
                        rentalDate.plusDays(3),
                        rentalDate.minusDays(1)
                )
        );
    }

    private Rental createRental(User user, Car car) {
        Rental rental = new Rental();
        rental.setRentalDate(LocalDate.of(2026, 7, 27));
        rental.setReturnDate(LocalDate.of(2026, 7, 30));
        rental.setUser(user);
        rental.setCar(car);
        return rental;
    }

    private Car saveCar() {
        Car car = new Car();
        car.setModel("Octavia");
        car.setBrand("Skoda");
        car.setType(CarType.SEDAN);
        car.setInventory(3);
        car.setDailyFee(new BigDecimal("49.99"));
        return carRepository.saveAndFlush(car);
    }

    private User saveUser(String email) {
        Role role = roleRepository.findByName(RoleName.CUSTOMER).orElseThrow();
        User user = new User();
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPassword("encoded-password");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }
}
