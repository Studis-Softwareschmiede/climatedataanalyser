package ch.studer.germanclimatedataanalyser.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Zentrale CORS-Konfiguration (ersetzt die früheren @CrossOrigin-Annotationen auf den 8 Controllern).
 *
 * Erlaubte Origins kommen aus app.cors.allowed-origins (komma-separiert).
 * Default: http://localhost:4200 (lokales ng-serve im Dev).
 * Prod: per Env-Variable APP_CORS_ALLOWED_ORIGINS überschreiben (Spring Relaxed Binding).
 *
 * Migration note: Spring-Boot 2.x (EOL seit 2023-11-18) — javax.*-Namespace; kein jakarta.*.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    // Komma-separierte Origin-Liste; in Prod via APP_CORS_ALLOWED_ORIGINS überschreiben.
    // Kein Wildcard — explizite Allowlist (security/R02).
    @Value("${app.cors.allowed-origins:http://localhost:4200}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);
    }
}
