package com.droneanalytics.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

/**
 * Global CORS configuration.
 *
 * Allows the React dev server (Vite default: http://localhost:5173) and any
 * localhost port to call the API during development.
 * In production, replace the allowed-origins pattern with your actual domain.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns(
                "http://localhost:*",   // Vite dev server (5173) + any local port
                "http://127.0.0.1:*"
            )
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("Authorization", "Content-Type", "Accept",
                            "X-Requested-With", "Cache-Control")
            .exposedHeaders("Authorization")
            .allowCredentials(false)
            .maxAge(3600);
    }
}
