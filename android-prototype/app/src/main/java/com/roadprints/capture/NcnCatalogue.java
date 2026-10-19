package com.roadprints.capture;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Indexed national reference; the byte-array constructor retains the trial golden contract. */
final class NcnCatalogue {
    static final String SCOPE="internal-eight-source-inventory-trial";
    static final String[] KEYS={"NCN:167","NCN:270","NCN:344","NCN:426","NCN:440","NCN:476","NCN:548","NCN:755"};
    final String version,attribution;
    final int heldCount;
    private android.database.sqlite.SQLiteDatabase database;
    double networkLength;
    final java.util.Map<String,Path> recent=new java.util.LinkedHashMap<String,Path>(128,.75f,true){protected boolean removeEldestEntry(java.util.Map.Entry<String,Path> e){return size()>128;}};
    final Map<String,JSONObject> routes=new LinkedHashMap<>();
    final List<Path> paths=new ArrayList<>();
    static final class Path {
        final JSONObject data;
        final String way,route,sourceHash;
        final JSONArray coordinates,nodes,bindings;
        final double[] positions;final double[][] xy;
        final java.util.Set<String> modes=new java.util.HashSet<>();
        Path(JSONObject value) throws Exception {
            data=value;way=value.getString("way");route=value.getString("route");sourceHash=value.getString("source_geometry_sha256");
            coordinates=value.getJSONArray("coordinates");nodes=value.getJSONArray("nodes");bindings=value.getJSONArray("bindings");
            if(!way.matches("[1-9][0-9]*")||coordinates.length()<2||coordinates.length()!=nodes.length()||sourceHash.length()!=64)throw new IllegalArgumentException("Source identity invalid");
            JSONArray origin=value.getJSONArray("origin"),saved=value.getJSONArray("positions");positions=new double[coordinates.length()];xy=new double[positions.length][];
            if(saved.length()!=positions.length)throw new IllegalArgumentException("Position count invalid");
            JSONArray m=value.getJSONArray("modes");for(int i=0;i<m.length();i++){String mode=m.getString(i);if(!mode.equals("on_foot")&&!mode.equals("cycling"))throw new IllegalArgumentException("Mode invalid");modes.add(mode);}
            for(int i=0;i<positions.length;i++) {
                JSONArray c=coordinates.getJSONArray(i);if(c.length()!=2||!RoadDetailGeometry.valid(c))throw new IllegalArgumentException("Coordinate invalid");NcnJson.node(nodes.get(i));
                xy[i]=project(c);positions[i]=saved.getDouble(i);
                if(!Double.isFinite(positions[i])||positions[i]<0||i==0&&positions[i]!=0||i>0&&positions[i]<positions[i-1])throw new IllegalArgumentException("Source positions invalid");
                if(i>0&&Math.abs((positions[i]-positions[i-1])-Math.hypot(xy[i][0]-xy[i-1][0],xy[i][1]-xy[i-1][1]))>1e-5)throw new IllegalArgumentException("Source lengths differ");
            }
        }
        double[] project(JSONArray p) throws Exception {JSONArray o=data.getJSONArray("origin");return new double[]{6371008.8*Math.toRadians(p.getDouble(0)-o.getDouble(0))*Math.cos(Math.toRadians(o.getDouble(1))),6371008.8*Math.toRadians(p.getDouble(1)-o.getDouble(1))};}
    }
    NcnCatalogue(byte[] raw,JSONObject manifest) throws Exception {
        heldCount=0;version=NcnJson.hash(raw);if(!version.equals(manifest.getString("sha256")))throw new IllegalArgumentException("NCN checksum differs");
        JSONObject root=new JSONObject(new String(raw,StandardCharsets.UTF_8));
        if(root.getInt("schema_version")!=1||!SCOPE.equals(root.getString("scope"))||root.getBoolean("whole_published_route_ready")||!root.getBoolean("physical_equivalence_verified"))throw new IllegalArgumentException("NCN scope differs");
        attribution=root.getString("attribution");JSONArray rs=root.getJSONArray("routes");
        for(int i=0;i<rs.length();i++) {
            JSONObject route=rs.getJSONObject(i);String key=route.getString("key");double length=route.getDouble("length_m"),sum=0;
            JSONObject logical=route.getJSONObject("logical");java.util.Iterator<String> keys=logical.keys();while(keys.hasNext()){double n=logical.getDouble(keys.next());if(!Double.isFinite(n)||n<=0)throw new IllegalArgumentException("Invalid NCN section");sum+=n;}
            if(!Double.isFinite(length)||length<=0||Math.abs(sum-length)>1e-5||routes.put(key,route)!=null)throw new IllegalArgumentException("Invalid NCN denominator");
        }
        if(!routes.keySet().equals(new java.util.HashSet<>(java.util.Arrays.asList(KEYS))))throw new IllegalArgumentException("NCN inventory incomplete");
        JSONArray ps=root.getJSONArray("paths");java.util.Set<String> identities=new java.util.HashSet<>();
        for(int i=0;i<ps.length();i++) {
            Path p=new Path(ps.getJSONObject(i));if(!routes.containsKey(p.route)||!identities.add(p.route+"/"+p.way))throw new IllegalArgumentException("Duplicate/unknown source");
            JSONObject logical=routes.get(p.route).getJSONObject("logical");
            for(int j=0;j<p.bindings.length();j++) {
                JSONObject b=p.bindings.getJSONObject(j);double a=b.getDouble("start"),z=b.getDouble("end"),low=b.getDouble("low"),high=b.getDouble("high"),length=logical.getDouble(b.getString("target"));
                if(!Double.isFinite(a)||!Double.isFinite(z)||!Double.isFinite(low)||!Double.isFinite(high)||a<0||z<=a||z>p.positions[p.positions.length-1]+1e-5||Math.min(low,high)<0||Math.max(low,high)>length+1e-5||low==high)throw new IllegalArgumentException("Invalid NCN binding");
            }paths.add(p);
        }
    }
    private static volatile NcnCatalogue loaded;
    private NcnCatalogue(android.database.sqlite.SQLiteDatabase db,JSONObject manifest)throws Exception {
        heldCount=manifest.getJSONArray("held_route_keys").length();database=db;version=manifest.getString("sha256");attribution=manifest.getString("attribution");networkLength=manifest.getDouble("network_length_m");
        try(android.database.Cursor cursor=db.rawQuery("SELECT key,data FROM routes ORDER BY key",null)){while(cursor.moveToNext())routes.put(cursor.getString(0),new JSONObject(cursor.getString(1)));}
        if(routes.size()!=manifest.getInt("supported_identities"))throw new IllegalArgumentException("Incomplete national checkpoint");
    }
    static synchronized NcnCatalogue load(Context context) throws Exception {
        if(loaded!=null)return loaded;
        NcnCatalogueFiles.Installed installed=NcnCatalogueFiles.open(context);JSONObject manifest=installed.manifest;java.io.File file=installed.file;
        loaded=new NcnCatalogue(android.database.sqlite.SQLiteDatabase.openDatabase(file.getPath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY),manifest);return loaded;
    }
    static synchronized void invalidate(){loaded=null;}
    interface TargetVisitor {void accept(String key,double length,JSONArray coordinates)throws Exception;}
    void targets(String route,TargetVisitor visitor)throws Exception{
        try(android.database.Cursor cursor=database.rawQuery("SELECT key,length,coords FROM targets WHERE route=?",new String[]{route})){
            while(cursor.moveToNext()){if(Thread.currentThread().isInterrupted())throw new InterruptedException();visitor.accept(cursor.getString(0),cursor.getDouble(1),new JSONArray(cursor.getString(2)));}
        }
    }
    JSONArray network(String route,String target)throws Exception{
        if(database==null)return new JSONArray();
        try(android.database.Cursor cursor=database.rawQuery("SELECT network FROM targets WHERE route=? AND key=?",new String[]{route,target})){
            return cursor.moveToFirst()?new JSONArray(cursor.getString(0)):new JSONArray();
        }
    }
    synchronized List<Path> query(String clause,String...args)throws Exception {
        List<Path> result=new ArrayList<>();
        if(database==null){for(Path p:paths){boolean include=clause.equals("route=?")?p.route.equals(args[0]):clause.equals("way=?")?p.way.equals(args[0]):clause.equals("hash=?")?p.sourceHash.equals(args[0]):true;if(include)result.add(p);}return result;}
        try(android.database.Cursor cursor=database.rawQuery("SELECT route,way,data FROM paths WHERE "+clause,args)){
            while(cursor.moveToNext()){String id=cursor.getString(0)+"/"+cursor.getString(1);Path p=recent.get(id);if(p==null){p=new Path(new JSONObject(new String(read(new GZIPInputStream(new java.io.ByteArrayInputStream(cursor.getBlob(2)))),StandardCharsets.UTF_8)));recent.put(id,p);}result.add(p);}
        }return result;
    }
    List<JSONArray> overview(String route)throws Exception{
        List<JSONArray> result=new ArrayList<>();if(database==null)return result;
        try(android.database.Cursor cursor=database.rawQuery("SELECT data FROM overview WHERE key=?",new String[]{route})){
            if(cursor.moveToFirst()){JSONArray lines=new JSONArray(new String(read(new GZIPInputStream(new java.io.ByteArrayInputStream(cursor.getBlob(0)))),StandardCharsets.UTF_8));for(int i=0;i<lines.length();i++)result.add(lines.getJSONArray(i));}
        }return result;
    }
    List<Path> route(String route)throws Exception{return query("route=?",route);}
    List<Path> way(String way)throws Exception{return query("way=?",way);}
    List<Path> hash(String hash)throws Exception{return query("hash=?",hash);}
    List<Path> nodes(long first,long last)throws Exception {
        if(database==null)return new ArrayList<>(paths);
        return query("way IN (SELECT a.way FROM nodes a JOIN nodes b ON a.way=b.way WHERE a.node=? AND b.node=?)",Long.toString(first),Long.toString(last));
    }
    static byte[] read(InputStream input) throws Exception {
        try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>64_000_000)throw new IllegalArgumentException("NCN input too large");out.write(buffer,0,n);}return out.toByteArray();}
    }
}
