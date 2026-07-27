package com.vdmytriv.carsharing.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vdmytriv.carsharing.TestcontainersConfiguration;
import com.vdmytriv.carsharing.model.Car;
import com.vdmytriv.carsharing.model.CarType;
import com.vdmytriv.carsharing.model.Role;
import com.vdmytriv.carsharing.model.RoleName;
import com.vdmytriv.carsharing.model.User;
import com.vdmytriv.carsharing.repository.CarRepository;
import com.vdmytriv.carsharing.repository.RoleRepository;
import com.vdmytriv.carsharing.repository.UserRepository;
import com.vdmytriv.carsharing.security.JwtService;
import java.math.BigDecimal;
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
class CarApiIntegrationTest {

    private static final String VALID_CAR_REQUEST = """
            {
              "model": "Corolla",
              "brand": "Toyota",
              "type": "SEDAN",
              "inventory": 4,
              "dailyFee": 49.90
            }
            """;

    @Autowired
    private CarRepository carRepository;

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
    void createCar_WithManagerToken_ReturnsCreatedCar() throws Exception {
        User manager = saveUser("manager@example.com", RoleName.MANAGER);

        mockMvc.perform(post("/cars")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CAR_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.model").value("Corolla"))
                .andExpect(jsonPath("$.brand").value("Toyota"))
                .andExpect(jsonPath("$.type").value("SEDAN"))
                .andExpect(jsonPath("$.inventory").value(4))
                .andExpect(jsonPath("$.dailyFee").value(49.90))
                .andExpect(jsonPath("$.deleted").doesNotExist());
    }

    @Test
    void createCar_WithCustomerToken_ReturnsForbidden() throws Exception {
        User customer = saveUser("customer@example.com", RoleName.CUSTOMER);

        mockMvc.perform(post("/cars")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CAR_REQUEST))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateAndDeleteCar_WithCustomerToken_ReturnForbidden() throws Exception {
        User customer = saveUser("customer@example.com", RoleName.CUSTOMER);
        Car car = saveCar("Civic", "Honda", CarType.SEDAN, 2, "50.00");
        String authorization = bearer(customer);

        mockMvc.perform(put("/cars/{id}", car.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CAR_REQUEST))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/cars/{id}", car.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inventory": 7
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/cars/{id}", car.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCar_WithoutAuthentication_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/cars")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CAR_REQUEST))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createCar_WithInvalidRequest_ReturnsFieldErrors() throws Exception {
        User manager = saveUser("manager@example.com", RoleName.MANAGER);

        mockMvc.perform(post("/cars")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": " ",
                                  "brand": "",
                                  "type": null,
                                  "inventory": -1,
                                  "dailyFee": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.model").exists())
                .andExpect(jsonPath("$.fieldErrors.brand").exists())
                .andExpect(jsonPath("$.fieldErrors.type").exists())
                .andExpect(jsonPath("$.fieldErrors.inventory").exists())
                .andExpect(jsonPath("$.fieldErrors.dailyFee").exists());
    }

    @Test
    void getCars_WithoutAuthentication_FiltersSortsAndPaginates() throws Exception {
        saveCar("Corolla", "Toyota", CarType.SEDAN, 3, "40.00");
        saveCar("RAV4", "Toyota", CarType.SUV, 0, "70.00");
        saveCar("Civic", "Honda", CarType.SEDAN, 2, "50.00");

        mockMvc.perform(get("/cars")
                        .param("brand", "toy")
                        .param("available", "true")
                        .param("minDailyFee", "30.00")
                        .param("maxDailyFee", "60.00")
                        .param("page", "0")
                        .param("size", "1")
                        .param("sort", "dailyFee,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].model").value("Corolla"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void getCar_WithoutAuthentication_ReturnsCar() throws Exception {
        Car car = saveCar("Civic", "Honda", CarType.SEDAN, 2, "50.00");

        mockMvc.perform(get("/cars/{id}", car.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(car.getId()))
                .andExpect(jsonPath("$.model").value("Civic"));
    }

    @Test
    void getCars_WithAvailableFalse_ReturnsCarsWithoutInventory() throws Exception {
        saveCar("Corolla", "Toyota", CarType.SEDAN, 3, "40.00");
        saveCar("RAV4", "Toyota", CarType.SUV, 0, "70.00");

        mockMvc.perform(get("/cars")
                        .param("available", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].model").value("RAV4"))
                .andExpect(jsonPath("$.content[0].inventory").value(0));
    }

    @Test
    void getCars_WithModelAndType_ReturnsMatchingCars() throws Exception {
        saveCar("Corolla", "Toyota", CarType.SEDAN, 3, "40.00");
        saveCar("Corolla Cross", "Toyota", CarType.SUV, 1, "65.00");
        saveCar("Civic", "Honda", CarType.SEDAN, 2, "50.00");

        mockMvc.perform(get("/cars")
                        .param("model", "corolla")
                        .param("type", "SEDAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].model").value("Corolla"))
                .andExpect(jsonPath("$.content[0].type").value("SEDAN"));
    }

    @Test
    void getCars_WithInvalidFeeRange_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/cars")
                        .param("minDailyFee", "80.00")
                        .param("maxDailyFee", "20.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Minimum daily fee cannot exceed maximum daily fee"
                ));
    }

    @Test
    void getCars_WithUnsupportedSortField_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/cars")
                        .param("sort", "deleted,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported sort field: deleted"));
    }

    @Test
    void getCars_WithPageSizeAboveLimit_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/cars")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Page size cannot exceed 100"));
    }

    @Test
    void updateCar_WithManagerToken_ReplacesCarData() throws Exception {
        User manager = saveUser("manager@example.com", RoleName.MANAGER);
        Car car = saveCar("Civic", "Honda", CarType.SEDAN, 2, "50.00");

        mockMvc.perform(put("/cars/{id}", car.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "CR-V",
                                  "brand": "Honda",
                                  "type": "SUV",
                                  "inventory": 5,
                                  "dailyFee": 75.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("CR-V"))
                .andExpect(jsonPath("$.type").value("SUV"))
                .andExpect(jsonPath("$.inventory").value(5))
                .andExpect(jsonPath("$.dailyFee").value(75.00));
    }

    @Test
    void patchCar_WithManagerToken_UpdatesOnlyProvidedFields() throws Exception {
        User manager = saveUser("manager@example.com", RoleName.MANAGER);
        Car car = saveCar("Civic", "Honda", CarType.SEDAN, 2, "50.00");

        mockMvc.perform(patch("/cars/{id}", car.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inventory": 7
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("Civic"))
                .andExpect(jsonPath("$.inventory").value(7))
                .andExpect(jsonPath("$.dailyFee").value(50.00));
    }

    @Test
    void patchCar_WithEmptyRequest_ReturnsBadRequest() throws Exception {
        User manager = saveUser("manager@example.com", RoleName.MANAGER);
        Car car = saveCar("Civic", "Honda", CarType.SEDAN, 2, "50.00");

        mockMvc.perform(patch("/cars/{id}", car.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "At least one car field must be provided"
                ));
    }

    @Test
    void deleteCar_WithManagerToken_HidesCarFromPublicApi() throws Exception {
        User manager = saveUser("manager@example.com", RoleName.MANAGER);
        Car car = saveCar("Civic", "Honda", CarType.SEDAN, 2, "50.00");

        mockMvc.perform(delete("/cars/{id}", car.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/cars/{id}", car.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Car not found: " + car.getId()));
    }

    @Test
    void getCar_WithUnknownId_ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/cars/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Car not found: 999999"));
    }

    @Test
    void createCar_WithUnknownType_ReturnsBadRequest() throws Exception {
        User manager = saveUser("manager@example.com", RoleName.MANAGER);

        mockMvc.perform(post("/cars")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "Corolla",
                                  "brand": "Toyota",
                                  "type": "COUPE",
                                  "inventory": 4,
                                  "dailyFee": 49.90
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request"));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user.getEmail());
    }

    private Car saveCar(
            String model,
            String brand,
            CarType type,
            int inventory,
            String dailyFee
    ) {
        Car car = new Car();
        car.setModel(model);
        car.setBrand(brand);
        car.setType(type);
        car.setInventory(inventory);
        car.setDailyFee(new BigDecimal(dailyFee));
        return carRepository.saveAndFlush(car);
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
