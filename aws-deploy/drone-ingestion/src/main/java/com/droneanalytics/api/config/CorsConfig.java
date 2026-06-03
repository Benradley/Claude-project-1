package com.droneanalytics.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

/**
 * Global CORS configuration — environment-variable driven.
 *
 * Set CORS_ALLOWED_ORIGINS in your Elastic Beanstalk environment variables
 * to your CloudFront distribution URL (or a comma-separated list of patterns).
 *
 * Example EB env var:
 *   CORS_ALLOWED_ORIGINS = https://d1a2b3c4xyz.cloudfront.net
 *
 * Default (local dev + Cloudflare tunnel):
 *   http://localhost:*,http://127.0.0.1:*,https://*.trycloudflare.com
 *
 * Pattern syntax: Spring's allowedOriginPatterns supports * wildcards,
 * so https://*.cloudfront.net would match all CloudFront distributions.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * Comma-separated list of allowed origin patterns.
     * Override via the CORS_ALLOWED_ORIGINS environment variable.
     */
    @Value("${CORS_ALLOWED_ORIGINS:http://localhost:*,http://127.0.0.1:*,https://*.trycloudflare.com}")
    private String allowedOriginsConfig;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Split the env var on commas; trim whitespace around each pattern.
        String[] patterns = allowedOriginsConfig.split(",");
        for (int i = 0; i < patterns.length; i++) {
            patterns[i] = patterns[i].trim();
        }

        registry.addMapping("/api/**")
            .allowedOriginPatterns(patterns)
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("Authorization", "Content-Type", "Accept",
                            "X-Requested-With", "Cache-Control")
            .exposedHeaders("Authorization")
            .allowCredentials(false)
            .maxAge(3600);
    }
}
