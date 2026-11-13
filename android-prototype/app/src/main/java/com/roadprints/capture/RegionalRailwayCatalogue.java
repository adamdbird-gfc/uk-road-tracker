package com.roadprints.capture;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.zip.GZIPInputStream;

final class RegionalRailwayCatalogue implements RailwayMatcher.Graph, AutoCloseable {
    static final String VERSION = "stage1-osm-20261112-r1";
    static final String SHA256 = "5409cf795b0ab58e4ef9911531b463c538c0a5e8c8555e3183ecb7d23c3dad5a";
    private static final int BYTES = 15183872;
    private final SQLiteDatabase db;
    private final Map<NodeKey,List<RailwayMatcher.Edge>> adjacency =
            new LinkedHashMap<NodeKey,List<RailwayMatcher.Edge>>(256,.75f,true) {
                @Override protected boolean removeEldestEntry(Map.Entry<NodeKey,List<RailwayMatcher.Edge>> entry) {
                    return size() > 256;
                }
            };
    private record NodeKey(String ref, long id) {}
    static final String COVERAGE = "london_north_kent(-0.16,51.41,0.39,51.55)";
    static final class QueryLimit extends RuntimeException {
        final String query;
        QueryLimit(String query) { this.query = query; }
    }

    static RegionalRailwayCatalogue open(Context context, File dir, BooleanSupplier stop) throws Exception {
        File file = new File(dir,VERSION+"-portable.sqlite");
        if (!file.isFile() || file.length() != BYTES) {
            File temp = File.createTempFile("railway-", ".tmp", dir);
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream input = new GZIPInputStream(context.getAssets().open("railway/regional.sqlite.gzip"));
                     FileOutputStream output = new FileOutputStream(temp)) {
                    byte[] buffer = new byte[32*1024];
                    int total = 0;
                    for (int n; (n = input.read(buffer)) != -1;) {
                        if (stop.getAsBoolean()) throw new java.util.concurrent.CancellationException();
                        if ((total += n) > BYTES) throw new IOException("Railway catalogue size");
                        digest.update(buffer,0,n);
                        output.write(buffer,0,n);
                    }
                    if (total != BYTES || !SHA256.equals(hex(digest.digest())))
                        throw new IOException("Railway catalogue checksum");
                    output.getFD().sync();
                }
                Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp.toPath());
            }
        }
        return new RegionalRailwayCatalogue(file);
    }

    RegionalRailwayCatalogue(File file) {
        db = SQLiteDatabase.openDatabase(file.getPath(),null,SQLiteDatabase.OPEN_READONLY
                | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        try (Cursor c = db.rawQuery("PRAGMA cache_size=-2048",null)) {
            c.moveToFirst();
        } catch (RuntimeException e) {
            db.close();
            throw e;
        }
    }

    static String hex(byte[] digest) {
        StringBuilder out = new StringBuilder(digest.length*2);
        for (byte b : digest) out.append(Character.forDigit((b>>>4)&15,16)).append(Character.forDigit(b&15,16));
        return out.toString();
    }

    @Override public List<RailwayMatcher.Edge> nearby(RailwayMatcher.Fix p, double radius, boolean road) {
        String table = road?"road":"rail";
        String fields = road?"e.id,0,0,ax,ay,bx,by,length,'',0"
                :"e.id,a,b,ax,ay,bx,by,length,ref,eligible AND NOT tunnel";
        return edges("SELECT "+fields+" FROM "+table+"_index s JOIN "+table+" e ON e.id=s.id"
                +" WHERE xmin BETWEEN CAST(? AS REAL)-1280 AND CAST(? AS REAL) AND xmax>=CAST(? AS REAL) AND ymin<=CAST(? AS REAL) AND ymax>=CAST(? AS REAL) LIMIT 257", bounds(p.x(),p.y(),radius), table);
    }

    private static String[] bounds(double x,double y,double r) {
        return new String[]{String.valueOf(x-r),String.valueOf(x+r),String.valueOf(x-r),String.valueOf(y+r),String.valueOf(y-r)};
    }

    @Override public List<RailwayMatcher.Edge> adjacent(String ref,long node) {
        return adjacency.computeIfAbsent(new NodeKey(ref,node), key -> {
            String id = String.valueOf(node);
            return edges("SELECT id,a,b,ax,ay,bx,by,length,ref,1 FROM rail WHERE (a=? OR b=?)"
                    +" AND ref=? AND eligible=1 AND tunnel=0 LIMIT 257",new String[]{id,id,ref}, "adjacency");
        });
    }

    private List<RailwayMatcher.Edge> edges(String query,String[] args,String name) {
        List<RailwayMatcher.Edge> out = new ArrayList<>();
        try (Cursor c = db.rawQuery(query,args)) {
            while (c.moveToNext()) {
                if (out.size() == 256) throw new QueryLimit(name);
                out.add(new RailwayMatcher.Edge(c.getLong(0),c.getLong(1),c.getLong(2),c.getDouble(3),
                        c.getDouble(4),c.getDouble(5),c.getDouble(6),c.getDouble(7),c.getString(8),c.getInt(9)!=0));
            }
        }
        return out;
    }

    @Override public RailwayMatcher.Axis axis(String ref,RailwayMatcher.Hit p) {
        double x = p.x(), y = p.y(), best = 24;
        RailwayMatcher.Axis result = null;
        String[] args = Arrays.copyOf(bounds(x,y,24),6);
        args[5] = ref;
        try (Cursor c = db.rawQuery("SELECT e.id,ax,ay,bx,by,length,start,assessable"
                +" FROM axis_index s JOIN axis e ON e.id=s.id"
                +" WHERE xmin BETWEEN CAST(? AS REAL)-1280 AND CAST(? AS REAL) AND xmax>=CAST(? AS REAL) AND ymin<=CAST(? AS REAL) AND ymax>=CAST(? AS REAL) AND ref=? ORDER BY e.id LIMIT 257",args)) {
            int count = 0;
            while (c.moveToNext()) {
                if (++count > 256) throw new QueryLimit("axis");
                var edge = new RailwayMatcher.Edge(c.getLong(0),0,0,c.getDouble(1),c.getDouble(2),
                        c.getDouble(3),c.getDouble(4),c.getDouble(5),ref,true);
                var hit = edge.hit(new RailwayMatcher.Fix(x,y,0,0));
                if (hit.residual() < best) {
                    best = hit.residual();
                    result = new RailwayMatcher.Axis(c.getDouble(6)+hit.fraction()*edge.length(),c.getInt(7)!=0);
                }
            }
        }
        return result;
    }

    @Override public boolean held(String ref,double lo,double hi) {
        try (Cursor c = db.rawQuery("SELECT 1 FROM axis WHERE ref=? AND assessable=0"
                +" AND start<CAST(? AS REAL) AND start+length>CAST(? AS REAL) LIMIT 1",
                new String[]{ref,String.valueOf(hi),String.valueOf(lo)})) {
            return c.moveToFirst();
        }
    }

    @Override public void close() { db.close(); }
}
