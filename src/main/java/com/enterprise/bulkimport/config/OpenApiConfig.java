package com.enterprise.bulkimport.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bulkImportOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Bulk Data Import & Validation System")
                        .description("Upload CSV/Excel files for streaming validation and import. "
                                + "Processing is asynchronous — POST /api/v1/imports returns "
                                + "immediately with a PENDING job; poll GET /api/v1/imports/{id} "
                                + "for status, and GET /api/v1/imports/{id}/summary once it "
                                + "reaches a terminal state (COMPLETED / PARTIALLY_COMPLETED / FAILED).")
                        .version("v1")
                        .contact(new Contact().name("Bulk Import Team")));
    }
}
