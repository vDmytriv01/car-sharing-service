package com.vdmytriv.carsharing;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("test")
class CarSharingServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void getHealthEndpoint_ReturnsUpStatus() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void getOpenApiDocument_ReturnsApplicationMetadata() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Car Sharing Service API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath(
                        "$.components.securitySchemes.bearerAuth.scheme"
                ).value("bearer"))
                .andExpect(jsonPath(
                        "$.components.securitySchemes.bearerAuth.bearerFormat"
                ).value("JWT"));
    }

    @Test
    void getOpenApiDocument_ExpandsSearchAndPaginationParameters() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/cars'].get.parameters[*].name"
                ).value(hasItems(
                        "model",
                        "brand",
                        "type",
                        "available",
                        "minDailyFee",
                        "maxDailyFee",
                        "page",
                        "size",
                        "sort"
                )))
                .andExpect(jsonPath(
                        "$.paths['/cars'].get.parameters"
                ).value(hasSize(9)))
                .andExpect(jsonPath(
                        "$.paths['/cars'].get.parameters[?(@.required == true)]"
                ).isEmpty())
                .andExpect(jsonPath(
                        "$.paths['/cars'].get.parameters[?(@.name == 'sort')]"
                                + ".schema.type"
                ).value("array"))
                .andExpect(jsonPath(
                        "$.paths['/rentals'].get.parameters[*].name"
                ).value(hasItems("user_id", "is_active", "page", "size", "sort")))
                .andExpect(jsonPath(
                        "$.paths['/rentals'].get.parameters"
                ).value(hasSize(5)))
                .andExpect(jsonPath(
                        "$.paths['/rentals'].get.parameters[?(@.required == true)]"
                ).isEmpty())
                .andExpect(jsonPath(
                        "$.paths['/rentals'].get.parameters[?(@.name == 'sort')]"
                                + ".schema.type"
                ).value("array"))
                .andExpect(jsonPath(
                        "$.paths['/payments'].get.parameters[*].name"
                ).value(hasItems("user_id", "page", "size", "sort")))
                .andExpect(jsonPath(
                        "$.paths['/payments'].get.parameters"
                ).value(hasSize(4)))
                .andExpect(jsonPath(
                        "$.paths['/payments'].get.parameters[?(@.required == true)]"
                ).isEmpty())
                .andExpect(jsonPath(
                        "$.paths['/payments'].get.parameters[?(@.name == 'sort')]"
                                + ".schema.type"
                ).value("array"));
    }
}
