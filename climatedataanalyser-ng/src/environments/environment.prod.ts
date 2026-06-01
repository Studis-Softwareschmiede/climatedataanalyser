// Same-origin: API + Frontend werden vom gleichen Server ausgeliefert.
// BASE_URL leer → ApiService konstruiert relative URLs (/api/...), Browser
// resolved gegen window.location.origin. Funktioniert für:
//   - Spring-Boot embedded (port 8092 oder anderer) ohne Context-Path
//   - Hinter Proxy/Tunnel auf 80/443
//   - Production-Tomcat mit eigenem Context-Path (Pfade dann via base href in index.html)
// Vorher: hardcoded localhost:8080/ClimateAnalyser brach den Docker-Spring-Boot-Setup
// (Adoption Live-Test 2026-06-01).
export const environment = {
  production: true,
  BASE_URL: ''
};
