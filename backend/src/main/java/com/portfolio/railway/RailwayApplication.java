package com.portfolio.railway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RailwayApplication {
    public static void main(String[] args) {
        SpringApplication.run(RailwayApplication.class, args);
    }
}
