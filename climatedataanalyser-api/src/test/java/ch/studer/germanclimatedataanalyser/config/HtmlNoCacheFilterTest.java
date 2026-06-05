package ch.studer.germanclimatedataanalyser.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-Test für {@link HtmlNoCacheFilter}: HTML-/SPA-Navigationsantworten müssen
 * {@code no-cache} bekommen (sonst serviert der Browser nach einem Deploy weiter die
 * alte index.html → alte Bundle-Hashes → „Deploy nicht angekommen"), statische
 * gehashte Assets und /api/**-Antworten dagegen NICHT.
 */
class HtmlNoCacheFilterTest {

    private final HtmlNoCacheFilter filter = new HtmlNoCacheFilter();

    private MockHttpServletResponse runFor(String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return response;
    }

    private void assertNoCache(String uri) throws Exception {
        String cc = runFor(uri).getHeader("Cache-Control");
        assertNotNull(cc, "Cache-Control muss gesetzt sein für " + uri);
        assertTrue(cc.contains("no-cache"), "no-cache erwartet für " + uri + ", war: " + cc);
        assertTrue(cc.contains("no-store"), "no-store erwartet für " + uri + ", war: " + cc);
    }

    private void assertUntouched(String uri) throws Exception {
        assertNull(runFor(uri).getHeader("Cache-Control"),
                "Cache-Control darf NICHT vom Filter gesetzt werden für " + uri);
    }

    @Test
    void rootIsNoCache() throws Exception {
        assertNoCache("/");
    }

    @Test
    void indexHtmlIsNoCache() throws Exception {
        assertNoCache("/index.html");
    }

    @Test
    void spaDeepRouteIsNoCache() throws Exception {
        assertNoCache("/database");
        assertNoCache("/analytics");
    }

    @Test
    void hashedBundleIsUntouched() throws Exception {
        assertUntouched("/main-AB12CD34.js");
        assertUntouched("/styles.5f3c.css");
        assertUntouched("/favicon.ico");
    }

    @Test
    void apiResponsesAreUntouched() throws Exception {
        assertUntouched("/api/database/");
        assertUntouched("/api/analytics/stations");
    }

    @Test
    void pragmaAndExpiresAreSetOnHtml() throws Exception {
        MockHttpServletResponse response = runFor("/database");
        assertEquals("no-cache", response.getHeader("Pragma"));
        assertEquals("0", response.getHeader("Expires"));
    }
}
