package com.droneanalytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Drone Flight Analytics SaaS — Spring Boot entry point.
 *
 * Starts an embedded Tomcat server on port 8080 (configurable via
 * application.properties or the SERVER_PORT environment variable).
 *
 * Run:  mvn spring-boot:run
 * Or:   java -jar target/drone-analytics.jar
 */
@SpringBootApplication
public class DroneAnalyticsApplication {
    public static void main(String[] args) {
        SpringApplication.run(DroneAnalyticsApplication.class, args);
    }
}
