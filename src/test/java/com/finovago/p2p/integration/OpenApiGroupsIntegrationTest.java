package com.finovago.p2p.integration;

import com.finovago.p2p.AbstractIntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Each Swagger group in OpenApiConfig is an explicit allowlist of paths, so a new endpoint is
 * silently absent from Swagger UI until it is added to one. These tests catch that omission.
 */
@DisplayName("OpenAPI groups Integration Tests")
class OpenApiGroupsIntegrationTest extends AbstractIntegrationTest {

    private static final String CURRENT_USER_PATH = "$.paths['/api/v1/auth/me'].get";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should document GET /auth/me in the customer-api group")
    void shouldDocumentCurrentUserEndpoint_inCustomerApiGroup() throws Exception {
        mockMvc.perform(get("/api-docs/customer-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(CURRENT_USER_PATH + ".summary").value("Get my profile"))
                .andExpect(jsonPath(CURRENT_USER_PATH + ".responses.200").exists())
                .andExpect(jsonPath(CURRENT_USER_PATH + ".responses.401").exists())
                .andExpect(jsonPath(CURRENT_USER_PATH + ".responses.403").exists());
    }

    @Test
    @DisplayName("Should document GET /auth/me in the admin-api group, since ADMIN can call it too")
    void shouldDocumentCurrentUserEndpoint_inAdminApiGroup() throws Exception {
        mockMvc.perform(get("/api-docs/admin-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(CURRENT_USER_PATH + ".summary").value("Get my profile"));
    }
}
