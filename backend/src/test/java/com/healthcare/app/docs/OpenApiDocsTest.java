package com.healthcare.app.docs;

import com.healthcare.app.support.ApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("OpenAPI documentation")
class OpenApiDocsTest extends ApiTest {

    @Test
    void specIsPublic_andDescribesEveryEndpointGroup() throws Exception {
        get("/v3/api-docs", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("HealthCare API"))
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/appointments']").exists())
                .andExpect(jsonPath("$.paths['/api/appointments/{id}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/appointments/summary']").exists())
                .andExpect(jsonPath("$.paths['/api/doctors']").exists())
                .andExpect(jsonPath("$.paths['/api/recommendations']").exists())
                .andExpect(jsonPath("$.paths['/api/risk/diabetes']").exists())
                .andExpect(jsonPath("$.paths['/api/chat/stream']").exists());
    }

    @Test
    void jwtBearerAuth_isDeclared_andPublicEndpointsOptOut() throws Exception {
        get("/v3/api-docs", null)
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").value(hasSize(0)));
    }

    @Test
    void listEndpoints_documentTheirPagingAndFilterParameters() throws Exception {
        get("/v3/api-docs", null)
                .andExpect(jsonPath("$.paths['/api/appointments'].get.parameters[*].name",
                        hasItems("status", "from", "to", "page", "size", "sort")))
                .andExpect(jsonPath("$.paths['/api/doctors'].get.parameters[*].name",
                        hasItems("specialty", "q", "page", "size", "sort")));
    }

    @Test
    void swaggerUi_isReachableWithoutLogin() throws Exception {
        // 200 or a redirect to the UI page, never 401/403
        int s = get("/swagger-ui.html", null).andReturn().getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertTrue(s == 200 || s == 302, "unexpected status " + s);
        get("/swagger-ui/index.html", null).andExpect(status().isOk());
    }
}
