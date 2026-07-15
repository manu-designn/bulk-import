package com.enterprise.bulkimport;

import com.enterprise.bulkimport.config.ImportProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(ImportProperties.class)
public class BulkImportApplication {

    public static void main(String[] args) {
        SpringApplication.run(BulkImportApplication.class, args);
    }
}
