package com.roadprints.capture;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/** Read-only packaged OSM reference. Only tiles intersecting the current matched leg are decoded. */
final class RoadReference implements AutoCloseable {
    static final String ASSET = "uk-road-reference-v1.bin";
    static final String ALGORITHM = "accepted-roads-v1";
    private static final int INDEX_START = 44;
    private static final int ENTRY_BYTES = 16;
    private static final int MAX_TILE_BYTES = 4 * 1024 * 1024;
    private static final double TILE_SCALE = 50;
    private static final double WEST = -9, SOUTH = 49, EAST = 3, NORTH = 62;

    private final FileChannel channel;
    private final long start;
    private final long length;
    private final ByteBuffer index;
    final String version;
    private final boolean version3;
    private final LinkedHashMap<Long, LocalRoadNetwork> tiles = new LinkedHashMap<>(16, .75f, true);

    // Read-only performance counters; consulted on the Map worker thread only.
    // Never part of the attribution identity or cache key.
    private long nearbyQueries, tileCacheHits, tileDecodes, candidateEdges;
    record Statistics(long queries, long hits, long decodes, long candidates) {}
    Statistics statistics() {
        return new Statistics(nearbyQueries, tileCacheHits, tileDecodes, candidateEdges);
    }

    RoadReference(FileChannel channel, long start, long length) throws IOException {
        this.channel = channel;
        this.start = start;
        this.length = length;
        ByteBuffer header = read(0, INDEX_START);
        byte[] magic = new byte[8];
        header.get(magic);
        String format = new String(magic, StandardCharsets.US_ASCII);
        if (!"RPRD0002".equals(format) && !"RPRD0003".equals(format))
            throw new IOException("Unsupported road reference");
        version3 = "RPRD0003".equals(format);
        byte[] digest = new byte[32];
        header.get(digest);
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest) {
            hex.append(Character.forDigit((value & 0xff) >>> 4, 16));
            hex.append(Character.forDigit(value & 15, 16));
        }
        version = hex.toString();
        int count = header.getInt();
        if (count <= 0 || count > 365_000) throw new IOException("Invalid road tile count");
        index = read(INDEX_START, count * ENTRY_BYTES);
    }

    Set<LocalRoadNetwork.Edge> nearby(double ax, double ay, double bx, double by) throws IOException {
        nearbyQueries++;
        Set<LocalRoadNetwork.Edge> edges = new HashSet<>();
        int left = tile(Math.min(ax, bx)), right = tile(Math.max(ax, bx));
        int bottom = tile(Math.min(ay, by)), top = tile(Math.max(ay, by));
        if (left < tile(WEST) || right > tile(EAST) || bottom < tile(SOUTH) || top > tile(NORTH))
            return edges;
        // Construct projected endpoints once and collect directly into the same
        // deduplicated set; do not create a temporary HashSet per intersected tile.
        LocalRoadNetwork.Point first = point(ax, ay), last = point(bx, by);
        for (int x = left; x <= right; x++) {
            for (int y = bottom; y <= top; y++) {
                LocalRoadNetwork network = tile(x, y);
                if (network != null) network.collectNearby(first, last, 8, edges);
            }
        }
        candidateEdges += edges.size();
        return edges;
    }

    static LocalRoadNetwork.Point point(double lon, double lat) {
        return LocalRoadNetwork.ukPoint(lon, lat);
    }

    private LocalRoadNetwork tile(int x, int y) throws IOException {
        long key = ((long) x << 32) | (y & 0xffffffffL);
        if (tiles.containsKey(key)) {
            tileCacheHits++;
            return tiles.get(key);
        }
        int low = 0, high = index.capacity() / ENTRY_BYTES - 1;
        LocalRoadNetwork network = null;
        while (low <= high) {
            int middle = (low + high) >>> 1, offset = middle * ENTRY_BYTES;
            int comparison = Integer.compare(x, index.getInt(offset));
            if (comparison == 0) comparison = Integer.compare(y, index.getInt(offset + 4));
            if (comparison < 0) high = middle - 1;
            else if (comparison > 0) low = middle + 1;
            else {
                network = decodeTile(index.getInt(offset + 8), index.getInt(offset + 12));
                tileDecodes++;
                break;
            }
        }
        if (tiles.size() == 8) tiles.remove(tiles.keySet().iterator().next());
        tiles.put(key, network);
        return network;
    }

    private LocalRoadNetwork decodeTile(int offset, int size) throws IOException {
        if (offset < INDEX_START + index.capacity() || size <= 0 || size > MAX_TILE_BYTES)
            throw new IOException("Invalid road tile location");
        LocalRoadNetwork network = new LocalRoadNetwork(WEST, SOUTH, EAST, NORTH, 54);
        byte[] compressed = read(offset, size).array();
        try (DataInputStream data = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(new ByteArrayInputStream(compressed))))) {
            int bytes = 0;
            while (true) {
                int first = data.read();
                if (first == -1) break;
                long way = first;
                for (int i = 1; i < 8; i++) way = (way << 8) | data.readUnsignedByte();
                long from = data.readLong(), to = data.readLong();
                double ax = data.readInt() / 1e7, ay = data.readInt() / 1e7;
                double bx = data.readInt() / 1e7, by = data.readInt() / 1e7;
                int direction = data.readByte(), flags = data.readUnsignedByte(), labelLength = data.readUnsignedShort();
                bytes += 44 + labelLength;
                if (labelLength > 1024 || bytes > MAX_TILE_BYTES || direction < -1 || direction > 2 || (version3 ? flags >>> 2 : flags >>> 1) >= 64)
                    throw new IOException("Invalid road tile data");
                byte[] label = new byte[labelLength];
                data.readFully(label);
                int mm = groundMillimetres(ax, ay, bx, by);
                String packedLabel = new String(label, StandardCharsets.UTF_8);
                int separator = version3 ? packedLabel.indexOf(31) : -1;
                String name = separator < 0 ? packedLabel : packedLabel.substring(0, separator);
                String roadRef = separator < 0 ? packedLabel : packedLabel.substring(separator + 1);
                boolean footOnly = version3 && (flags & 2) != 0;
                int roadClass = version3 ? flags >>> 2 : flags >>> 1;
                LocalRoadNetwork.Edge edge = new LocalRoadNetwork.Edge(way, from, to,
                        name, point(ax, ay), point(bx, by),
                        direction == 2 ? null : direction,
                        (flags & 1) != 0 || footOnly, mm, roadClass, roadRef, footOnly);
                // Overlapping extract boundaries may supply the same way.
                if (!network.edges.containsKey(edge.id())) network.add(edge);
            }
        }
        return network;
    }

    static int groundMillimetres(double ax, double ay, double bx, double by) {
        double longitudeScale = Math.cos(Math.toRadians((ay + by) / 2));
        return (int) Math.round(111_195_080 * Math.hypot((bx - ax) * longitudeScale, by - ay));
    }

    private static int tile(double value) {
        return (int) Math.floor(value * TILE_SCALE);
    }

    private ByteBuffer read(long relativeOffset, int size) throws IOException {
        if (relativeOffset < 0 || relativeOffset > length - size) throw new EOFException("Road reference truncated");
        ByteBuffer data = ByteBuffer.allocate(size);
        while (data.hasRemaining()) {
            if (channel.read(data, start + relativeOffset + data.position()) <= 0)
                throw new EOFException("Road reference truncated");
        }
        data.flip();
        return data;
    }

    @Override public void close() throws IOException {
        channel.close();
    }
}
