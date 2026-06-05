package ch.studer.germanclimatedataanalyser.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Verhindert, dass der Browser die {@code index.html} der Angular-SPA cached.
 *
 * <p><b>Warum:</b> Spring liefert für die {@code index.html} (Root {@code /}, Deep-Links wie
 * {@code /database}, {@code /index.html}) keinen {@code Cache-Control}-Header. Der Browser wendet
 * dann <i>heuristisches</i> Caching an (abgeleitet aus {@code Last-Modified}) und serviert nach
 * einem Deploy weiter die <b>alte</b> {@code index.html} — die auf die <b>alten</b>, content-gehashten
 * Bundles ({@code main.&lt;hash&gt;.js}) zeigt. Folge: ein frisch deploytes Image wirkt im Browser
 * stundenlang „nicht angekommen" (alter Code), obwohl der Server längst die neue Version ausliefert.
 *
 * <p><b>Fix:</b> HTML-Navigationsantworten (Root, extensionslose SPA-Routen, {@code *.html}) werden
 * mit {@code no-cache} ausgeliefert → der Browser revalidiert die {@code index.html} bei jedem Aufruf
 * und zieht sofort die neuen Bundle-Hashes. Die gehashten Assets selbst dürfen weiterhin lange gecached
 * werden (neuer Inhalt ⇒ neuer Dateiname ⇒ neue URL ⇒ kein Stale-Risiko) und bleiben hier unangetastet.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HtmlNoCacheFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isHtmlNavigation(request.getRequestURI())) {
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
        }
        filterChain.doFilter(request, response);
    }

    /**
     * True für Antworten, die die {@code index.html} sind (oder es per SPA-Fallback werden):
     * Root {@code /}, {@code *.html} oder eine extensionslose Angular-Route ({@code /database},
     * {@code /analytics} …). {@code /api/**} und alle Pfade mit Datei-Endung (= statische Assets)
     * sind ausgenommen.
     */
    private boolean isHtmlNavigation(String uri) {
        if (uri == null) {
            return false;
        }
        if (uri.startsWith("/api/") || uri.equals("/api")) {
            return false;   // echte REST-Endpunkte: kein SPA, kein no-cache nötig
        }
        String lastSegment = uri.substring(uri.lastIndexOf('/') + 1);
        if (lastSegment.isEmpty()) {
            return true;    // Root "/" → index.html
        }
        if (lastSegment.endsWith(".html")) {
            return true;    // index.html & Co.
        }
        return !lastSegment.contains(".");   // extensionslos → SPA-Deep-Route → index.html-Fallback
    }
}
