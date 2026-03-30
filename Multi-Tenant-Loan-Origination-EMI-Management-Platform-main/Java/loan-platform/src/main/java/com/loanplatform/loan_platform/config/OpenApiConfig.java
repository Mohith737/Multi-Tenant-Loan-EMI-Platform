package com.loanplatform.loan_platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI platformOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Loan Platform API").version("v1"))
                .schemaRequirement("basicAuth", new SecurityScheme()
                        .name("basicAuth")
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("basic"))
                .schemaRequirement("bearerAuth", new SecurityScheme()
                        .name("bearerAuth")
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));
    }
}
