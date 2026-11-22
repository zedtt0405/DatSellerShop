package com.DatShop.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI smartFashionOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Smart Fashion E-Commerce API")
                        .description("REST API for Smart Fashion E-Commerce, AI Outfit and TikTok Trend Analysis. "
                                + "Error bodies use ApiError for 400, 401, 403, 409, and 500.")
                        .version("v1"))
                .servers(List.of(new Server().url("http://localhost:8080").description("Local development")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Paste the accessToken from POST /api/auth/login. Do not include the Bearer prefix.")));
    }
}
