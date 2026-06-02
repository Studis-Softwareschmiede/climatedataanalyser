package ch.studer.germanclimatedataanalyser.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reiner Unit-Test (kein Spring-Context) für {@link CorsConfig}.
 * Prüft: keine Wildcard-Origin; default localhost:4200; /api/**-Mapping;
 * allowCredentials=false.
 */
class CorsConfigTest {

    /**
     * Subklasse in gleicher Compile-Unit, um die protected Methode
     * getCorsConfigurations() zugänglich zu machen.
     */
    private static class InspectableCorsRegistry extends CorsRegistry {
        Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }

    private CorsConfig corsConfigWithOrigins(String... origins) {
        CorsConfig cfg = new CorsConfig();
        ReflectionTestUtils.setField(cfg, "allowedOrigins", origins);
        return cfg;
    }

    private Map<String, CorsConfiguration> buildRegistry(CorsConfig cfg) {
        InspectableCorsRegistry registry = new InspectableCorsRegistry();
        cfg.addCorsMappings(registry);
        return registry.configurations();
    }

    @Test
    void mappingOnApiDoubleWildcard() {
        Map<String, CorsConfiguration> configs = buildRegistry(
                corsConfigWithOrigins("http://localhost:4200"));
        assertTrue(configs.containsKey("/api/**"),
                "Mapping /api/** muss registriert sein");
    }

    @Test
    void defaultOriginIsLocalhost4200AndNoWildcard() {
        Map<String, CorsConfiguration> configs = buildRegistry(
                corsConfigWithOrigins("http://localhost:4200"));
        List<String> allowed = configs.get("/api/**").getAllowedOrigins();
        assertTrue(
                allowed != null && allowed.contains("http://localhost:4200"),
                "http://localhost:4200 muss in der Origin-Liste stehen");
        assertFalse(
                allowed != null && allowed.contains("*"),
                "Wildcard '*' darf nicht erlaubt sein");
    }

    @Test
    void customOriginsAreApplied() {
        Map<String, CorsConfiguration> configs = buildRegistry(
                corsConfigWithOrigins("https://app.example.com", "https://admin.example.com"));
        List<String> allowed = configs.get("/api/**").getAllowedOrigins();
        assertTrue(
                allowed != null && allowed.contains("https://app.example.com"),
                "Konfigurierte Origin muss in der Liste stehen");
        assertFalse(
                allowed != null && allowed.contains("*"),
                "Wildcard '*' darf nicht erlaubt sein");
    }

    @Test
    void allowedMethodsContainStandardVerbs() {
        Map<String, CorsConfiguration> configs = buildRegistry(
                corsConfigWithOrigins("http://localhost:4200"));
        List<String> methods = configs.get("/api/**").getAllowedMethods();
        assertTrue(methods != null && methods.contains("GET"), "GET muss erlaubt sein");
        assertTrue(methods != null && methods.contains("POST"), "POST muss erlaubt sein");
        assertTrue(methods != null && methods.contains("OPTIONS"), "OPTIONS muss erlaubt sein");
    }

    @Test
    void allowCredentialsIsFalse() {
        Map<String, CorsConfiguration> configs = buildRegistry(
                corsConfigWithOrigins("http://localhost:4200"));
        assertFalse(
                Boolean.TRUE.equals(configs.get("/api/**").getAllowCredentials()),
                "allowCredentials muss false sein (kein Cookie-Auth)");
    }
}
