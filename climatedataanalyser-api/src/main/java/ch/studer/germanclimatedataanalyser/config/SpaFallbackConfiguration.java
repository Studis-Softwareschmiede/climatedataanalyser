package ch.studer.germanclimatedataanalyser.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * SPA-Fallback für die Angular-Single-Page-App.
 *
 * <p>Ohne dies liefert ein Hard-Reload / Deep-Link auf eine client-seitige Angular-Route
 * (z.B. /analytics, /database) eine Whitelabel-404, weil Spring Boot dafür keinen
 * Static-File und kein Controller-Mapping findet. Lösung: existiert kein statisches File
 * und ist es keine /api-Route, wird index.html ausgeliefert — der Angular-Router übernimmt
 * dann client-seitig.
 *
 * <p>Die Angular-Bundles/Assets (haben eine Datei-Endung) werden weiterhin direkt
 * ausgeliefert; @RestController-Mappings unter /api/** haben Vorrang vor diesem
 * Resource-Handler und bleiben unberührt (echte 404 bei unbekannten /api-Pfaden).
 */
@Configuration
public class SpaFallbackConfiguration implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/resources/")   // dorthin kopiert der Build die Angular-dist
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;   // echtes File (index.html, main.<hash>.js, styles.css, favicon.ico …)
                        }
                        if (resourcePath.startsWith("api/")) {
                            return null;        // API: kein SPA-Fallback → reguläre 404
                        }
                        return location.createRelative("index.html");   // Angular-Deep-Route → SPA
                    }
                });
    }
}
