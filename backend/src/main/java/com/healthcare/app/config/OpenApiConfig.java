package com.healthcare.app.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Documentation for the whole API. Interactive UI: /swagger-ui.html, raw spec: /v3/api-docs.
 * Every endpoint requires the bearer token by default; the public ones opt out with @SecurityRequirements.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "HealthCare API",
                version = "1.0",
                description = """
                        Patients and doctors: login, appointments, a symptom-based doctor recommender, \
                        an AI health chat (streaming) and a diabetes risk check.

                        **How to try it:** call POST /api/auth/login, copy the `token` from the response, \
                        click **Authorize** and paste it.

                        All errors share one shape: `{ timestamp, status, message, fieldErrors? }`. \
                        List endpoints are paged: `?page=0&size=10&sort=field,desc` (size 1-100)."""),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT",
        description = "JWT from POST /api/auth/login")
public class OpenApiConfig {
}
