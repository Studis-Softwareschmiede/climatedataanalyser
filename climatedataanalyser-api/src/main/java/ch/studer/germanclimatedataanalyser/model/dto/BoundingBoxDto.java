package ch.studer.germanclimatedataanalyser.model.dto;

/**
 * Response-DTO für GET /api/stations/bbox?bundesland={name}.
 *
 * Bewusst eigene innere {@link Coordinate}-Klasse — nicht das geteilte GpsPoint —,
 * damit der Response-Kontrakt minimal bleibt (nur latitude + longitude). GpsPoint
 * wird im Request-Pfad genutzt und exponiert dort validity-Flags
 * (longitudeValid, latitudeValid, gpsNull), die in der Response nichts zu
 * suchen haben (kein Spec-Drift, kein Leak interner Sentinel-Werte).
 */
public class BoundingBoxDto {

    private Coordinate nw;
    private Coordinate se;

    public BoundingBoxDto() {
    }

    public BoundingBoxDto(Coordinate nw, Coordinate se) {
        this.nw = nw;
        this.se = se;
    }

    public Coordinate getNw() {
        return nw;
    }

    public void setNw(Coordinate nw) {
        this.nw = nw;
    }

    public Coordinate getSe() {
        return se;
    }

    public void setSe(Coordinate se) {
        this.se = se;
    }

    /**
     * Minimaler Coordinate-Typ ausschließlich für die BBox-Response.
     */
    public static class Coordinate {

        private double latitude;
        private double longitude;

        public Coordinate() {
        }

        public Coordinate(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public double getLatitude() {
            return latitude;
        }

        public void setLatitude(double latitude) {
            this.latitude = latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public void setLongitude(double longitude) {
            this.longitude = longitude;
        }
    }
}
