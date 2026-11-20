package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.nio.charset.StandardCharsets;

/**
 * User-authorised, read-only evidence from the EXACT prepared MapRoutes used
 * by the overview renderer. Strict budget avoids copying the full archive.
 * Coordinates are private location data: never auto-upload or write logs.
 */
final class MapGeometryEvidenceExport {
    private MapGeometryEvidenceExport() {}
    static final int MAX_SECTIONS = 512;
    static final int MAX_POINTS = 8192;
    static final int MAX_BYTES = 384 * 1024;

    static JSONObject capture(MapActivity.MapRoutes routes, MapCorridorGeometry.Bounds area,
                              double[] camera, long revision, String filterId) throws Exception {
        if (routes == null || area == null || camera == null || camera.length != 3
                || !Double.isFinite(camera[0]) || !Double.isFinite(camera[1])
                || !Double.isFinite(camera[2]) || filterId == null)
            throw new IllegalArgumentException("Missing prepared Map export state");
        JSONArray segments = new JSONArray();
        int[] counts = {0,0};
        append(segments, routes.sections, area, "matched_corridors", counts);
        append(segments, routes.ferrySections, area, "ferries", counts);
        append(segments, routes.coveredMotorwaySections, area, "covered_motorways", counts);
        append(segments, routes.coveredARoadSections, area, "covered_a_roads", counts);
        // Iterate compact local intervals directly; do NOT enumerate the
        // lazy localRoadLines list or materialise the entire UK road network.
        for (LocalRoadCoverage.Line interval : routes.localRoadSegments)
            add(segments, interval.points(), area, "accepted_local_roads", counts);
        JSONObject metadata = new JSONObject()
                .put("format","roadprints_map_geometry_evidence")
                .put("format_version",1)
                .put("map_revision",revision)
                .put("filter",filterId)
                .put("camera",new JSONArray(java.util.Arrays.asList(
                        camera[0],camera[1],camera[2])))
                .put("window",new JSONObject()
                        .put("west",area.west()).put("south",area.south())
                        .put("east",area.east()).put("north",area.north()))
                .put("international_coverage_incomplete",routes.internationalCoverageIncomplete)
                .put("foot_fallback_incomplete",routes.footFallbackIncomplete)
                .put("local_coverage_degraded",routes.localCoverageDegraded)
                .put("local_coverage_resource_degraded",routes.localCoverageResourceDegraded)
                .put("matched_journey_count",routes.matchedJourneys)
                .put("display_sections",counts[0])
                .put("display_points",counts[1])
                .put("scope","visible_travelled_map_geometry_only")
                .put("segments",segments);
        if (metadata.toString().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("Map evidence exceeds 384 KiB export budget");
        return metadata;
    }

    private static void append(JSONArray output, List<JSONArray> lines,
            MapCorridorGeometry.Bounds area,String layer,int[] counts) throws Exception {
        for (JSONArray line : lines) add(output,line,area,layer,counts);
    }

    private static void add(JSONArray output,JSONArray line,
            MapCorridorGeometry.Bounds area,String layer,int[] counts) throws Exception {
        for(JSONArray piece:MapCorridorGeometry.clip(line,area)) {
            if (counts[0] >= MAX_SECTIONS || piece.length() > MAX_POINTS - counts[1])
                throw new IllegalArgumentException(
                        "Visible Map too dense: zoom in for a smaller evidence export");
            output.put(new JSONObject().put("layer",layer).put("geometry",piece));
            counts[0]++;
            counts[1]+=piece.length();
        }
    }
}
