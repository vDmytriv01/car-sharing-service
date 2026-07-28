package com.vdmytriv.carsharing.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vdmytriv.carsharing.TestcontainersConfiguration;
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
import com.vdmytriv.carsharing.security.JwtService;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class RentalApiIntegrationTest {

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RentalRepository rentalRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createRental_WithAvailableCar_ReturnsRentalAndDecreasesInventory()
            throws Exception {
        User customer = saveUser("customer@example.com", RoleName.CUSTOMER);
        Car car = saveCar("Corolla", "Toyota", 2);
        LocalDate expectedRentalDate = LocalDate.now();
        LocalDate returnDate = expectedRentalDate.plusDays(3);

        mockMvc.perform(post("/rentals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "returnDate": "%s",
                                  "carId": %d
                                }
                                """.formatted(returnDate, car.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.rentalDate")
                        .value(expectedRentalDate.toString()))
                .andExpect(jsonPath("$.returnDate").value(returnDate.toString()))
                .andExpect(jsonPath("$.actualReturnDate").doesNotExist())
                .andExpect(jsonPath("$.userId").value(customer.getId()))
                .andExpect(jsonPath("$.car.id").value(car.getId()))
                .andExpect(jsonPath("$.car.model").value("Corolla"))
                .andExpect(jsonPath("$.car.brand").value("Toyota"));

        mockMvc.perform(get("/cars/{id}", car.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inventory").value(1));
    }

    @Test
    void getRentals_AsCustomer_ReturnsOnlyOwnActiveRentals() throws Exception {
        User customer = saveUser("customer@example.com", RoleName.CUSTOMER);
        User anotherCustomer = saveUser("another@example.com", RoleName.CUSTOMER);
        Car activeCar = saveCar("Corolla", "Toyota", 1);
        Car returnedCar = saveCar("Civic", "Honda", 1);
        Rental activeRental = saveRental(customer, activeCar, null);
        saveRental(customer, returnedCar, LocalDate.now());
        saveRental(anotherCustomer, activeCar, null);

        mockMvc.perform(get("/rentals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .param("is_active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(activeRental.getId()))
                .andExpect(jsonPath("$.content[0].car.model").value("Corolla"))
                .andExpect(jsonPath("$.content[0].userId").value(customer.getId()));
    }

    @Test
    void getRental_AsOwnerAndManager_ReturnsDetailedRental() throws Exception {
        User customer = saveUser("customer@example.com", RoleName.CUSTOMER);
        User manager = saveUser("manager@example.com", RoleName.MANAGER);
        Car car = saveCar("Corolla", "Toyota", 1);
        Rental rental = saveRental(customer, car, null);

        mockMvc.perform(get("/rentals/{id}", rental.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(rental.getId()))
                .andExpect(jsonPath("$.car.id").value(car.getId()))
                .andExpect(jsonPath("$.car.model").value("Corolla"))
                .andExpect(jsonPath("$.userId").value(customer.getId()));

        mockMvc.perform(get("/rentals/{id}", rental.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(rental.getId()));
    }

    @Test
    void getRental_AsDifferentCustomer_ReturnsNotFound() throws Exception {
        User owner = saveUser("owner@example.com", RoleName.CUSTOMER);
        User anotherCustomer = saveUser("another@example.com", RoleName.CUSTOMER);
        Rental rental = saveRental(owner, saveCar("Civic", "Honda", 1), null);

        mockMvc.perform(get("/rentals/{id}", rental.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(anotherCustomer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Rental not found: " + rental.getId()));
    }

    @Test
    void getRentals_AsManager_FiltersByUserAndReturnedStatus() throws Exception {
        User manager = saveUser("manager@example.com", RoleName.MANAGER);
        User firstCustomer = saveUser("first@example.com", RoleName.CUSTOMER);
        User secondCustomer = saveUser("second@example.com", RoleName.CUSTOMER);
        Car car = saveCar("Corolla", "Toyota", 2);
        saveRental(firstCustomer, car, null);
        Rental returnedRental = saveRental(
                secondCustomer,
                car,
                LocalDate.now()
        );

        mockMvc.perform(get("/rentals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .param("user_id", secondCustomer.getId().toString())
                        .param("is_active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id")
                        .value(returnedRental.getId()))
                .andExpect(jsonPath("$.content[0].userId")
                        .value(secondCustomer.getId()));
    }

    @Test
    void getRentals_AsCustomer_IgnoresRequestedUserId() throws Exception {
        User customer = saveUser("customer@example.com", RoleName.CUSTOMER);
        User anotherCustomer = saveUser("another@example.com", RoleName.CUSTOMER);
        Rental ownRental = saveRental(
                customer,
                saveCar("Corolla", "Toyota", 1),
                null
        );
        saveRental(
                anotherCustomer,
                saveCar("Civic", "Honda", 1),
                null
        );

        mockMvc.perform(get("/rentals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .param("user_id", anotherCustomer.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ownRental.getId()));
    }

    @Test
    void createRental_WithUnavailableCar_ReturnsBadRequest() throws Exception {
        User customer = saveUser("customer@example.com", RoleName.CUSTOMER);
        Car car = saveCar("Corolla", "Toyota", 0);

        mockMvc.perform(post("/rentals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "returnDate": "%s",
                                  "carId": %d
                                }
                                """.formatted(LocalDate.now().plusDays(2), car.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Car is not available for rental"));
    }

    @Test
    void createRental_WithPastReturnDate_ReturnsFieldError() throws Exception {
        User customer = saveUser("customer@example.com", RoleName.CUSTOMER);
        Car car = saveCar("Corolla", "Toyota", 1);

        mockMvc.perform(post("/rentals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "returnDate": "%s",
                                  "carId": %d
                                }
                                """.formatted(LocalDate.now().minusDays(1), car.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.returnDate").exists());
    }

    @Test
    void rentals_WithoutAuthentication_ReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/rentals"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/rentals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "returnDate": "2030-01-01",
                                  "carId": 1
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getRentals_WithUnsupportedSortField_ReturnsBadRequest() throws Exception {
        User customer = saveUser("customer@example.com", RoleName.CUSTOMER);

        mockMvc.perform(get("/rentals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .param("sort", "user,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Unsupported sort field: user"));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user.getEmail());
    }

    private Car saveCar(String model, String brand, int inventory) {
        Car car = new Car();
        car.setModel(model);
        car.setBrand(brand);
        car.setType(CarType.SEDAN);
        car.setInventory(inventory);
        car.setDailyFee(new BigDecimal("50.00"));
        return carRepository.saveAndFlush(car);
    }

    private Rental saveRental(User user, Car car, LocalDate actualReturnDate) {
        Rental rental = new Rental();
        rental.setRentalDate(LocalDate.now().minusDays(2));
        rental.setReturnDate(LocalDate.now().plusDays(2));
        rental.setActualReturnDate(actualReturnDate);
        rental.setCar(car);
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
