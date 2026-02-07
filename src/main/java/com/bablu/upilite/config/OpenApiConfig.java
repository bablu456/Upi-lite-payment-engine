package com.bablu.upilite.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

        @Bean
        public OpenAPI upiLiteOpenAPI() {
                final String securitySchemeName = "bearerAuth";

                return new OpenAPI()
                                .info(new Info()
                                                .title("UPI-Lite Payment Engine API")
                                                .description("High-performance payment system documentation. Built with Spring Boot, Kafka & Docker.\n\n"
                                                                +
                                                                "**Authentication:** JWT Bearer Token required for protected endpoints.\n"
                                                                +
                                                                "Use `/api/users/login` to get a token, then click 'Authorize' button above.")
                                                .version("v1.0.0")
                                                .contact(new Contact()
                                                                .name("Bablu Kumar")
                                                                .email("bablu@example.com")
                                                                .url("https://github.com/bablu456"))
                                                .license(new License()
                                                                .name("Apache 2.0")
                                                                .url("http://springdoc.org")))
                                // Add JWT Security Schema
                                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                                .components(new Components()
                                                .addSecuritySchemes(securitySchemeName,
                                                                new SecurityScheme()
                                                                                .name(securitySchemeName)
                                                                                .type(SecurityScheme.Type.HTTP)
                                                                                .scheme("bearer")
                                                                                .bearerFormat("JWT")
                                                                                .description("Enter JWT token (without 'Bearer ' prefix)")));
        }
}