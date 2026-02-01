package com.cuenti.homebanking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Cuenti Homebanking Application.
 * 
 * This is a demo application showcasing enterprise Java skills using:
 * - Spring Boot for application framework
 * - Spring Security for authentication and authorization
 * - Spring Data JPA for persistence
 * - PostgreSQL for database
 * - Vaadin for UI
 */
@SpringBootApplication
public class HomebankingApplication {

    public static void main(String[] args) {
        SpringApplication.run(HomebankingApplication.class, args);
    }
}
