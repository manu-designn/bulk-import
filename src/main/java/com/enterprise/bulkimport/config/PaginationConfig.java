package com.enterprise.bulkimport.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;
import org.springframework.context.annotation.Bean;

@Configuration
public class PaginationConfig {

    private static final int MAX_PAGE_SIZE = 500;
    private static final int DEFAULT_PAGE_SIZE = 20;

    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer paginationCustomizer() {
        return resolver -> {
            resolver.setMaxPageSize(MAX_PAGE_SIZE);
            resolver.setFallbackPageable(
                    org.springframework.data.domain.PageRequest.of(0, DEFAULT_PAGE_SIZE));
        };
    }
}