package com.vdmytriv.carsharing.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vdmytriv.carsharing.TestcontainersConfiguration;
import com.vdmytriv.carsharing.dto.rental.RentalCreateRequest;
import com.vdmytriv.carsharing.exception.InvalidRequestException;
import com.vdmytriv.carsharing.model.Car;
import com.vdmytriv.carsharing.model.CarType;
import com.vdmytriv.carsharing.model.Rental;
import com.vdmytriv.carsharing.model.Role;
import com.vdmytriv.carsharing.model.RoleName;
import com.vdmytriv.carsharing.model.User;
import com.vdmytriv.carsharing.repository.CarRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class RentalConcurrencyIntegrationTest {

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RentalRepository rentalRepository;

    @Autowired
    private RentalService rentalService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        rentalRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        carRepository.deleteAllInBatch();
    }

    @Test
    void createRental_Concurrently_RentsLastAvailableCarOnlyOnce()
            throws Exception {
        User firstCustomer = saveUser("first.customer@example.com");
        User secondCustomer = saveUser("second.customer@example.com");
        Car car = saveCar(1);
        RentalCreateRequest request = new RentalCreateRequest(
                LocalDate.now().plusDays(2),
                car.getId()
        );

        List<Boolean> results = createWithLockContention(
                firstCustomer.getEmail(),
                secondCustomer.getEmail(),
                request
        );

        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, rentalRepository.count());
        assertEquals(0, carRepository.findById(car.getId()).orElseThrow()
                .getInventory());
    }

    @Test
    void returnRental_Concurrently_ReturnsOnlyOnce() throws Exception {
        User customer = saveUser("customer@example.com");
        Car car = saveCar(0);
        Rental rental = saveRental(customer, car);

        List<Boolean> results = returnConcurrently(
                customer.getEmail(),
                rental.getId(),
                rental.getId()
        );

        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        Rental returnedRental = rentalRepository.findById(rental.getId())
                .orElseThrow();
        assertNotNull(returnedRental.getActualReturnDate());
        assertEquals(1, carRepository.findById(car.getId()).orElseThrow()
                .getInventory());
    }

    @Test
    void returnDifferentRentals_Concurrently_IncreasesInventoryTwice()
            throws Exception {
        User customer = saveUser("customer@example.com");
        Car car = saveCar(0);
        Rental firstRental = saveRental(customer, car);
        Rental secondRental = saveRental(customer, car);

        List<Boolean> results = returnConcurrently(
                customer.getEmail(),
                firstRental.getId(),
                secondRental.getId()
        );

        assertTrue(results.stream().allMatch(Boolean::booleanValue));
        assertEquals(2, carRepository.findById(car.getId()).orElseThrow()
                .getInventory());
    }

    private List<Boolean> returnConcurrently(
            String email,
            Long firstRentalId,
            Long secondRentalId
    ) throws Exception {
        return runConcurrently(
                returnTask(email, firstRentalId),
                returnTask(email, secondRentalId)
        );
    }

    private List<Boolean> createWithLockContention(
            String firstEmail,
            String secondEmail,
            RentalCreateRequest request
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch secondCreateStarted = new CountDownLatch(1);
        CountDownLatch secondCreateFinished = new CountDownLatch(1);
        CountDownLatch releaseFirstLock = new CountDownLatch(1);
        TransactionTemplate transactionTemplate = new TransactionTemplate(
                transactionManager
        );

        try {
            final Future<Boolean> first = executor.submit(() -> createWhileHoldingLock(
                    transactionTemplate,
                    firstEmail,
                    request,
                    firstLockAcquired,
                    releaseFirstLock
            ));
            assertTrue(firstLockAcquired.await(5, SECONDS));
            final Future<Boolean> second = executor.submit(() -> {
                secondCreateStarted.countDown();
                try {
                    return tryCreateRental(secondEmail, request);
                } finally {
                    secondCreateFinished.countDown();
                }
            });
            assertTrue(secondCreateStarted.await(5, SECONDS));
            assertFalse(secondCreateFinished.await(500, MILLISECONDS));
            releaseFirstLock.countDown();
            return List.of(
                    first.get(10, SECONDS),
                    second.get(10, SECONDS)
            );
        } finally {
            releaseFirstLock.countDown();
            executor.shutdownNow();
        }
    }

    private boolean createWhileHoldingLock(
            TransactionTemplate transactionTemplate,
            String email,
            RentalCreateRequest request,
            CountDownLatch lockAcquired,
            CountDownLatch releaseLock
    ) {
        Boolean result = transactionTemplate.execute(status -> {
            carRepository.findActiveByIdForUpdate(request.carId())
                    .orElseThrow();
            lockAcquired.countDown();
            await(releaseLock);
            return tryCreateRental(email, request);
        });
        return Boolean.TRUE.equals(result);
    }

    private List<Boolean> runConcurrently(
            Callable<Boolean> firstOperation,
            Callable<Boolean> secondOperation
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(awaitStart(
                    firstOperation,
                    ready,
                    start
            ));
            Future<Boolean> second = executor.submit(awaitStart(
                    secondOperation,
                    ready,
                    start
            ));
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

    private Callable<Boolean> returnTask(
            String email,
            Long rentalId
    ) {
        return () -> {
            try {
                rentalService.returnRental(email, rentalId);
                return true;
            } catch (InvalidRequestException exception) {
                return false;
            }
        };
    }

    private boolean tryCreateRental(
            String email,
            RentalCreateRequest request
    ) {
        try {
            rentalService.create(email, request);
            return true;
        } catch (InvalidRequestException exception) {
            assertEquals(
                    "Car is not available for rental",
                    exception.getMessage()
            );
            return false;
        }
    }

    private Callable<Boolean> awaitStart(
            Callable<Boolean> operation,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return () -> {
            ready.countDown();
            start.await();
            return operation.call();
        };
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting for concurrent operation",
                    exception
            );
        }
    }

    private Car saveCar(int inventory) {
        Car car = new Car();
        car.setModel("Corolla");
        car.setBrand("Toyota");
        car.setType(CarType.SEDAN);
        car.setInventory(inventory);
        car.setDailyFee(new BigDecimal("50.00"));
        return carRepository.saveAndFlush(car);
    }

    private Rental saveRental(User user, Car car) {
        Rental rental = new Rental();
        rental.setRentalDate(LocalDate.now().minusDays(2));
        rental.setReturnDate(LocalDate.now().plusDays(2));
        rental.setCar(car);
        rental.setUser(user);
        return rentalRepository.saveAndFlush(rental);
    }

    private User saveUser(String email) {
        Role customerRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow();
        User user = new User();
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPassword(passwordEncoder.encode("Password123"));
        user.setRole(customerRole);
        return userRepository.saveAndFlush(user);
    }
}
