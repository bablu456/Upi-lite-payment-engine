package com.bablu.upilite.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI upiLiteOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("UPI-Lite Payment Engine API")
                        .description("High-performance payment system documentation. Built with Spring Boot, Kafka & Docker.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Bablu Kumar")
                                .email("bablu@example.com")
                                .url("https://github.com/bablu456"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("http://springdoc.org")));
    }
}