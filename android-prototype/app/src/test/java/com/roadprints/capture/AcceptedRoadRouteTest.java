package com.roadprints.capture;

import static org.junit.Assert.*;
import static com.roadprints.capture.LocalRoadNetwork.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class AcceptedRoadRouteTest {
    private static Edge edge(long way, long from, long to, double x0, double x1, double y) {
        return new Edge(way, from, to, "", new Point(x0, y), new Point(x1, y), 0, true);
    }

    @Test public void connectsAdjacentEdgesAndClipsPartialTravelInBothDirections() {
        Edge first = edge(1, 1, 2, 0, 50, 0), next = edge(2, 2, 3, 50, 100, 0);
        var out = CanonicalRoadAttribution.attributeAcceptedLeg(new Point(25, 0), new Point(50, 0),
                50, Set.of(first, next));
        var back = CanonicalRoadAttribution.attributeAcceptedLeg(new Point(75, 0), new Point(-50, 0),
                50, Set.of(first, next));
        assertNull(out.unresolvedReason());
        assertNull(back.unresolvedReason());
        assertEquals(List.of(new Span(first, new Interval(25000, 50000)), new Span(next, new Interval(0, 25000))),
                out.alignments().stream().map(CanonicalRoadAttribution.Alignment::span).toList());
        assertEquals(out.alignments().get(0).span(), back.alignments().get(1).span());
        assertEquals(out.alignments().get(1).span(), back.alignments().get(0).span());
    }

    @Test public void disconnectedAndMateriallyAmbiguousAlignmentsRemainUnresolved() {
        Edge first = edge(1, 1, 2, 0, 50, 0), next = edge(2, 20, 3, 50, 100, 0);
        assertNotNull(CanonicalRoadAttribution.attributeAcceptedLeg(new Point(25, 0), new Point(50, 0),
                50, Set.of(first, next)).unresolvedReason());
        Edge north = edge(3, 4, 5, 0, 100, 4), south = edge(4, 6, 7, 0, 100, 0);
        assertEquals("competing_alignment", CanonicalRoadAttribution.attributeAcceptedLeg(
                new Point(20, 2), new Point(60, 0), 60, Set.of(north, south)).unresolvedReason());
        var aligned = CanonicalRoadAttribution.attributeAcceptedLeg(
                new Point(20, .03), new Point(60, 0), 60, Set.of(north, south));
        assertEquals(south, aligned.alignments().get(0).span().edge());
    }

    @Test public void sparseSavedTimelineCreditsOnlyEffectiveMatchedMinorIntervals() throws Exception {
        Path pack = Files.createTempFile("roadprints-test-roads", ".bin");
        try {
            Files.write(pack, testPack());
            try (RoadReference reference = new RoadReference(FileChannel.open(pack), 0, Files.size(pack))) {
                var journey = new JSONObject("""
                        {"mode":"driving","processing_status":"complete","source":{"type":"google_timeline"},
                         "route_geometry":{"type":"LineString","coordinates":[[0.001,51.001],[0.005,51.001]]},
                         "processing_result":{"status":"ok","geojson":{"features":[
                           {"properties":{"confidence":0.001},"geometry":{"type":"LineString","coordinates":[
                             [0.0015,51.001],[0.002,51.001],[0.003,51.001],[0.004,51.001]]}},
                           {"geometry":{"type":"LineString","coordinates":[[0.003,51.001],[0.002,51.001]]}}
                         ]}}}
                        """);
                var complete = AcceptedRoadRoute.project(reference, journey);
                assertEquals(2, complete.sections().size());
                assertTrue(complete.majorOrOtherMm() > 0);
                assertEquals(1, complete.majorSections().size());
                Edge major = complete.majorSections().keySet().iterator().next();
                assertFalse(major.eligible);
                assertEquals(3L, major.way);
                assertEquals(1, complete.majorSections().get(major).size());
                assertEquals(0, complete.unresolvedMm());
                assertEquals(1, complete.residual().size());
                journey.put("journey_corrections", new JSONObject().put("removed_matched_segments", new JSONArray().put(1)));
                var corrected = AcceptedRoadRoute.project(reference, journey);
                assertEquals(1, corrected.sections().size());
                assertTrue(corrected.excludedMm() > 0);
                assertTrue(corrected.majorSections().isEmpty());
                var remaining = corrected.sections().entrySet().iterator().next();
                assertEquals(1, remaining.getKey().way);
                assertTrue(remaining.getValue().get(0).start() > 0);
                journey.put("processing_status", "pending");
                assertNull(AcceptedRoadRoute.project(reference, journey));
            }
        } finally {
            Files.deleteIfExists(pack);
        }
    }

    @Test public void savedWalkingRouteUsesSameCanonicalRoadIdsAndRespectsRemovedSegments()
            throws Exception {
        Path pack = Files.createTempFile("roadprints-foot-reconciliation", ".bin");
        try {
            Files.write(pack, testPack());
            try (RoadReference reference = new RoadReference(
                    FileChannel.open(pack), 0, Files.size(pack))) {
                JSONObject walk = new JSONObject("""
                        {"mode":"walking","processing_status":"complete",
                         "processing_result":{"status":"ok","geojson":{"features":[{
                         "geometry":{"type":"LineString","coordinates":[
                         [0.0015,51.001],[0.002,51.001],[0.003,51.001]]}}]}}}
                        """);
                JSONObject drive = new JSONObject(walk.toString()).put("mode", "driving");
                var walked = AcceptedRoadRoute.project(reference, walk);
                var driven = AcceptedRoadRoute.project(reference, drive);
                assertNotNull(walked);
                assertEquals(driven.sections().keySet(), walked.sections().keySet());
                assertEquals(driven.sections(), walked.sections());
                assertEquals(driven.majorSections(), walked.majorSections());
                walk.put("journey_corrections", new JSONObject()
                        .put("removed_matched_segments", new JSONArray().put(0)));
                var edited = AcceptedRoadRoute.project(reference, walk);
                assertNotNull(edited);
                assertTrue(edited.excludedMm() > 0);
                assertTrue(edited.sections().size() <= walked.sections().size());
                assertEquals(0, edited.unresolvedMm());
            }
        } finally {
            Files.deleteIfExists(pack);
        }
    }

    private static byte[] testPack() throws Exception {
        ByteArrayOutputStream compressedBytes = new ByteArrayOutputStream();
        try (DataOutputStream compressed = new DataOutputStream(new GZIPOutputStream(compressedBytes))) {
            for (int i = 1; i <= 3; i++) {
                compressed.writeLong(i);
                compressed.writeLong(i);
                compressed.writeLong(i + 1);
                compressed.writeInt(i * 10_000);
                compressed.writeInt(510_010_000);
                compressed.writeInt((i + 1) * 10_000);
                compressed.writeInt(510_010_000);
                compressed.writeByte(0);
                compressed.writeByte(i == 3 ? 0 : 1);
                compressed.writeShort(0);
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream pack = new DataOutputStream(bytes)) {
            pack.writeBytes("RPRD0002");
            pack.write(new byte[32]);
            pack.writeInt(1);
            pack.writeInt(0);
            pack.writeInt(2550);
            pack.writeInt(60);
            pack.writeInt(compressedBytes.size());
            compressedBytes.writeTo(pack);
        }
        return bytes.toByteArray();
    }
}
