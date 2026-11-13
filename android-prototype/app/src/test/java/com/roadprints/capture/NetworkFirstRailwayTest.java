package com.roadprints.capture;

import static org.junit.Assert.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPInputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class NetworkFirstRailwayTest {
    private static final String ASSET = "railway/network-first-r1.bin.gz";
    private static final long START = 1_790_000_000_000L;
    private static final long END = START + 60 * 60_000;
    private record Segment(double x, double y, double dx, double dy, double metres, boolean tunnel) {}

    @Test public void packagedRoutesInferAcrossGapsAndKeepUnobservedBranchesAmbiguous() throws Exception {
        var app = RuntimeEnvironment.getApplication();
        var assets = app.getAssets();
        var fixes = publicGeometryFixture(assets.open(ASSET));
        var network = NetworkFirstRailway.open(assets.open(ASSET), () -> false);
        try (var graph = RegionalRailwayCatalogue.open(app, app.getFilesDir(), () -> false)) {
            var result = network.infer(fixes, START, END, graph, () -> false);
            assertEquals("inferred", result.confidence());
            assertEquals("Sidcup", result.branch());
            assertEquals(39_228, result.metres(), 150);
            var sparse = fixes.stream().filter(fix -> fix.time() <= START + 12 * 60_000
                    || fix.time() >= START + 54 * 60_000).toList();
            var ambiguous = network.infer(sparse, START, END, graph, () -> false);
            assertEquals("ambiguous", ambiguous.confidence());
            assertEquals(0, ambiguous.metres(), 0);
            assertThrows(CancellationException.class, () -> network.infer(fixes, START, END, graph, () -> true));
            assertEquals("inferred", network.infer(fixes, START, END, graph, () -> false).confidence());
        }
        assertThrows(CancellationException.class, () -> NetworkFirstRailway.open(assets.open(ASSET), () -> true));
    }

    // Public geometry control for CI. The private owner recording is replayed separately.
    static List<RailwayMatcher.Fix> publicGeometryFixture(InputStream source) throws IOException {
        try (var input = new DataInputStream(new GZIPInputStream(source))) {
            assertEquals(0x52504e31, input.readInt());
            int count = input.readUnsignedShort();
            int routes = input.readUnsignedShort();
            input.skipNBytes(24L * input.readUnsignedByte());
            var edges = new ArrayList<Segment>();
            for (int i = 0; i < count; i++) {
                input.readInt();
                boolean tunnel = (input.readUnsignedByte() & 2) != 0;
                edges.add(new Segment(700000 + input.readInt()/100.0, 5700000 + input.readInt()/100.0,
                        input.readInt()/100.0, input.readInt()/100.0, input.readInt()/1000.0, tunnel));
            }
            for (int i = 0; i < routes; i++) {
                int branch = input.readUnsignedByte();
                boolean qualified = input.readBoolean();
                input.skipNBytes(2);
                int n = input.readUnsignedShort();
                var geometry = new ArrayList<Segment>();
                for (int j = 0; j < n; j++) geometry.add(edges.get(input.readUnsignedShort()));
                if (branch == 0 && qualified) return sampled(geometry);
            }
        }
        throw new AssertionError("Qualified corridor fixture missing");
    }

    private static List<RailwayMatcher.Fix> sampled(List<Segment> geometry) {
        var fixes = new ArrayList<RailwayMatcher.Fix>();
        double length = geometry.stream().mapToDouble(Segment::metres).sum();
        double offset = 0;
        int edge = 0;
        for (int i = 0; i <= 360; i++) {
            double distance = length * i / 360;
            while (edge + 1 < geometry.size() && offset + geometry.get(edge).metres < distance) {
                offset += geometry.get(edge++).metres;
            }
            Segment segment = geometry.get(edge);
            if (segment.tunnel || (i > 70 && i < 81)) continue;
            double part = (distance - offset) / segment.metres;
            fixes.add(new RailwayMatcher.Fix(segment.x + part*segment.dx,
                    segment.y + part*segment.dy, 8, START + i*10_000L));
        }
        return fixes;
    }
}
