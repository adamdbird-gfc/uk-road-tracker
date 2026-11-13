package com.roadprints.capture;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.GZIPInputStream;
import org.json.JSONObject;

/** Read-only, spatially indexed railway and nearby road geometry. */
final class NationalRailwayCatalogue implements NationalRailway.Graph, AutoCloseable {
    static final String VERSION = "uk-osm-20261112-r2";
    static final String SOURCE_SHA256 = "4d9cac6df2a62d850e2ec647b54ca919d78eb3ba4bcec05557a83aa238b92721";
    private static final long MAX_BYTES = 180_000_000;
    private final SQLiteDatabase db;
    final String sha256;
    private final Map<Long, List<NationalRailway.Track>> adjacency =
            new LinkedHashMap<>(256, .75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Long, List<NationalRailway.Track>> eldest) {
                    return size() > 8192;
                }
            };

    static NationalRailwayCatalogue open(Context app, BooleanSupplier stop) throws Exception {
        JSONObject manifest;
        try (var source = app.getAssets().open("railway/national.json")) {
            var bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            for (int count; (count = source.read(buffer)) != -1;) {
                if (bytes.size()+count > 8192) throw new IOException("National railway manifest size");
                bytes.write(buffer,0,count);
            }
            manifest = new JSONObject(new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
        }
        String sha = manifest.getString("sha256");
        long bytes = manifest.getLong("bytes");
        if (!VERSION.equals(manifest.getString("version")) || bytes < 1 || bytes > MAX_BYTES
                || !SOURCE_SHA256.equals(manifest.getString("source_sha256"))
                || !sha.matches("[0-9a-f]{64}")) throw new IOException("National railway manifest");
        File target = new File(app.getFilesDir(), VERSION+"-"+sha+".sqlite");
        if (!target.isFile() || target.length() != bytes) {
            File temp = File.createTempFile("national-rail-", ".tmp", app.getFilesDir());
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (var input = new GZIPInputStream(app.getAssets().open("railway/national.sqlite.gzip"));
                     var output = new FileOutputStream(temp)) {
                    byte[] buffer = new byte[32*1024];
                    long written = 0;
                    for (int count; (count = input.read(buffer)) != -1;) {
                        if (stop.getAsBoolean()) throw new CancellationException();
                        written += count;
                        if (written > bytes) throw new IOException("National railway asset length");
                        digest.update(buffer, 0, count);
                        output.write(buffer, 0, count);
                    }
                    if (written != bytes || !sha.equals(RegionalRailwayCatalogue.hex(digest.digest())))
                        throw new IOException("National railway asset checksum");
                    output.getFD().sync();
                }
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp.toPath());
            }
        }
        return new NationalRailwayCatalogue(target, sha);
    }

    NationalRailwayCatalogue(File file, String sha256) {
        this.sha256 = sha256;
        db = SQLiteDatabase.openDatabase(file.getPath(), null,
                SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        try (Cursor cursor = db.rawQuery("PRAGMA user_version", null)) {
            if (!cursor.moveToFirst() || cursor.getInt(0) != 1)
                throw new IllegalArgumentException("National railway schema");
        } catch (RuntimeException failure) {
            db.close();
            throw failure;
        }
        try (Cursor cursor = db.rawQuery("PRAGMA cache_size=-4096", null)) {
            cursor.moveToFirst();
        } catch (RuntimeException failure) {
            db.close();
            throw failure;
        }
    }

    @Override public List<NationalRailway.Station> stations(RailwayMatcher.Fix fix, double radius) {
        List<NationalRailway.Station> matches = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("SELECT s.id,s.name,s.x,s.y,a.node FROM station s"
                +" JOIN attachment a ON a.station=s.id"
                +" WHERE s.x BETWEEN CAST(? AS REAL) AND CAST(? AS REAL)"
                +" AND s.y BETWEEN CAST(? AS REAL) AND CAST(? AS REAL)"
                +" ORDER BY s.id,a.node LIMIT 65", new String[]{String.valueOf(fix.x()-radius),
                        String.valueOf(fix.x()+radius),String.valueOf(fix.y()-radius),String.valueOf(fix.y()+radius)})) {
            int rows = 0;
            while (cursor.moveToNext()) {
                if (++rows > 64) throw new RegionalRailwayCatalogue.QueryLimit("station");
                double distance = Math.hypot(cursor.getDouble(2)-fix.x(), cursor.getDouble(3)-fix.y());
                if (distance <= radius) matches.add(new NationalRailway.Station(
                        cursor.getLong(0),cursor.getLong(4),cursor.getString(1)));
            }
        }
        return matches;
    }

    @Override public List<NationalRailway.Track> adjacent(long node) {
        return adjacency.computeIfAbsent(node, id -> {
            List<NationalRailway.Track> matches = new ArrayList<>();
            String key = String.valueOf(id);
            try (Cursor cursor = db.rawQuery("SELECT id,a,b,ax,ay,bx,by,metres,tunnel,ref FROM edge"
                    +" WHERE a=? OR b=? ORDER BY id LIMIT 33", new String[]{key,key})) {
                while (cursor.moveToNext()) {
                    if (matches.size() == 32) throw new RegionalRailwayCatalogue.QueryLimit("rail_node");
                    var edge = new RailwayMatcher.Edge(cursor.getLong(0),cursor.getLong(1),cursor.getLong(2),
                            cursor.getDouble(3),cursor.getDouble(4),cursor.getDouble(5),cursor.getDouble(6),
                            cursor.getDouble(7),cursor.getString(9),true);
                    matches.add(new NationalRailway.Track(edge,cursor.getInt(8)!=0));
                }
            }
            return matches;
        });
    }

    @Override public double roadResidual(RailwayMatcher.Fix fix) {
        double nearest = 24;
        int count = 0;
        try (Cursor cursor = db.rawQuery("SELECT DISTINCT r.id,ax,ay,bx,by FROM road_cell c"
                +" JOIN road r ON r.id=c.id WHERE c.x BETWEEN CAST(? AS INTEGER) AND CAST(? AS INTEGER)"
                +" AND c.y BETWEEN CAST(? AS INTEGER) AND CAST(? AS INTEGER) LIMIT 257",
                new String[]{cell(fix.x()-24),cell(fix.x()+24),cell(fix.y()-24),cell(fix.y()+24)})) {
            while (cursor.moveToNext()) {
                if (++count > 256) throw new RegionalRailwayCatalogue.QueryLimit("national_road");
                var edge = new RailwayMatcher.Edge(0,0,0,cursor.getDouble(1),cursor.getDouble(2),
                        cursor.getDouble(3),cursor.getDouble(4),1,"",false);
                nearest = Math.min(nearest,edge.hit(fix).residual());
            }
        }
        return nearest;
    }

    private static String cell(double metres) {
        return String.valueOf((int)Math.floor(metres/128));
    }

    @Override public void close() { db.close(); }
}
