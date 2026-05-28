package com.diploma.mrt;

import com.diploma.mrt.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackageClasses = AppProperties.class)
public class MrtApplication {

    public static void main(String[] args) {
        SpringApplication.run(MrtApplication.class, args);
    }
}
