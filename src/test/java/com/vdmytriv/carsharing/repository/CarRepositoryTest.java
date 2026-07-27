package com.vdmytriv.carsharing.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vdmytriv.carsharing.TestcontainersConfiguration;
import com.vdmytriv.carsharing.model.Car;
import com.vdmytriv.carsharing.model.CarType;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@ActiveProfiles("test")
class CarRepositoryTest {

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void save_WithValidCar_PersistsAllFields() {
        Car car = createCar();

        Car savedCar = carRepository.saveAndFlush(car);
        entityManager.clear();

        Car persistedCar = carRepository.findById(savedCar.getId()).orElseThrow();
        assertThat(persistedCar.getModel()).isEqualTo("Octavia");
        assertThat(persistedCar.getBrand()).isEqualTo("Skoda");
        assertThat(persistedCar.getType()).isEqualTo(CarType.SEDAN);
        assertThat(persistedCar.getInventory()).isEqualTo(3);
        assertThat(persistedCar.getDailyFee()).isEqualByComparingTo("49.99");
    }

    @Test
    void delete_WithPersistedCar_ExcludesCarFromRepositoryQueries() {
        Car savedCar = carRepository.saveAndFlush(createCar());

        carRepository.delete(savedCar);
        carRepository.flush();
        entityManager.clear();

        assertThat(carRepository.findByIdAndDeletedFalse(savedCar.getId())).isEmpty();
        assertThat(carRepository.findAllByDeletedFalse(Pageable.unpaged()))
                .noneMatch(car -> car.getId().equals(savedCar.getId()));

        Car archivedCar = carRepository.findById(savedCar.getId()).orElseThrow();
        assertThat(archivedCar.isDeleted()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("invalidInventoryAndFee")
    void save_WithInvalidInventoryOrFee_IsRejectedByDatabase(
            int inventory,
            BigDecimal dailyFee
    ) {
        Car car = createCar();
        car.setInventory(inventory);
        car.setDailyFee(dailyFee);

        assertThatThrownBy(() -> carRepository.saveAndFlush(car))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Stream<Arguments> invalidInventoryAndFee() {
        return Stream.of(
                Arguments.of(-1, new BigDecimal("49.99")),
                Arguments.of(1, BigDecimal.ZERO)
        );
    }

    private Car createCar() {
        Car car = new Car();
        car.setModel("Octavia");
        car.setBrand("Skoda");
        car.setType(CarType.SEDAN);
        car.setInventory(3);
        car.setDailyFee(new BigDecimal("49.99"));
        return car;
    }
}
