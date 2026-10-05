package com.roadprints.capture;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** Bundled public station data: lookup never transmits a device location. */
final class RailStationCatalog {
    static final class Station {
        final String code, name;
        final double latitude, longitude, radius;
        Station(String code, String name, double latitude, double longitude) {
            this.code = code; this.name = name; this.latitude = latitude; this.longitude = longitude;
            // Large termini need to include the platform ends, not just the concourse.
            this.radius = "STP".equals(code) ? 650 : ("SHF".equals(code) ? 350 : 250);
        }
    }
    final List<Station> stations = new ArrayList<>();
    static RailStationCatalog read(Reader source) throws IOException {
        RailStationCatalog catalog = new RailStationCatalog();
        BufferedReader reader = new BufferedReader(source);
        reader.readLine();
        String line;
        while ((line = reader.readLine()) != null) {
            // The bundled CSV has no embedded newlines; quoted commas are supported.
            List<String> values = csvFields(line);
            if (values.size() < 4) continue;
            try {
                double lat = Double.parseDouble(values.get(1)), lon = Double.parseDouble(values.get(2));
                if (Double.isFinite(lat) && Double.isFinite(lon) && lat >= 49 && lat <= 61 && lon >= -9 && lon <= 3)
                    catalog.stations.add(new Station(values.get(3), values.get(0), lat, lon));
            } catch (NumberFormatException ignored) { /* Skip invalid reference rows. */ }
        }
        if (catalog.stations.size() < 2000) throw new IOException("Incomplete rail station reference");
        return catalog;
    }
    Station nearest(double lat, double lon, float accuracy) {
        if (!Float.isFinite(accuracy) || accuracy <= 0 || accuracy > 80) return null;
        Station best = null; double bestDistance = Double.POSITIVE_INFINITY;
        for (Station station : stations) {
            if (Math.abs(lat - station.latitude) > .01 || Math.abs(lon - station.longitude) > .02) continue;
            double distance = metres(lat, lon, station.latitude, station.longitude);
            if (distance <= station.radius && distance < bestDistance) { best = station; bestDistance = distance; }
        }
        return best;
    }
    static double metres(double lat1, double lon1, double lat2, double lon2) {
        double a = Math.pow(Math.sin(Math.toRadians(lat2-lat1)/2),2)
                + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(Math.toRadians(lon2-lon1)/2),2);
        return 6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0,1-a)));
    }
    private static List<String> csvFields(String line) {
        List<String> values = new ArrayList<>(); StringBuilder field = new StringBuilder(); boolean quoted = false;
        for (int i=0;i<line.length();i++) {
            char c=line.charAt(i);
            if (c=='"') {
                if (quoted && i+1<line.length() && line.charAt(i+1)=='"') { field.append(c); i++; }
                else quoted=!quoted;
            } else if(c==',' && !quoted) { values.add(field.toString()); field.setLength(0); }
            else field.append(c);
        }
        values.add(field.toString()); return values;
    }
}
