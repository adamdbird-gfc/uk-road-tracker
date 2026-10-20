package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.math.BigDecimal;
import java.util.TreeSet;

/** Python-compatible canonical evidence hashes; not a source of membership. */
final class NcnJson {
    static String hash(byte[] bytes) {
        try {byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    static String digest(Object value) {return hash(canonical(value).getBytes(StandardCharsets.UTF_8));}
    static String geometry(JSONArray points) throws Exception {
        JSONArray normalized=new JSONArray();
        if(points==null||points.length()<2)throw new IllegalArgumentException("Geometry required");
        for(int i=0;i<points.length();i++) {
            JSONArray p=points.getJSONArray(i);if(p.length()!=2||!RoadDetailGeometry.valid(p))throw new IllegalArgumentException("Invalid geometry");
            normalized.put(new JSONArray().put(p.getDouble(0)==0?0d:p.getDouble(0)).put(p.getDouble(1)==0?0d:p.getDouble(1)));
        }
        return digest(normalized);
    }
    static String sourceGeometry(JSONArray points)throws Exception{
        java.util.List<double[]> normalized=new java.util.ArrayList<>();
        for(int i=0;i<points.length();i++){JSONArray c=points.getJSONArray(i);double x=Math.rint(c.getDouble(0)*1e7)/1e7,y=Math.rint(c.getDouble(1)*1e7)/1e7;if(x==0)x=0;if(y==0)y=0;
            if(normalized.isEmpty()||normalized.get(normalized.size()-1)[0]!=x||normalized.get(normalized.size()-1)[1]!=y)normalized.add(new double[]{x,y});}
        boolean reverse=false;for(int i=0;i<normalized.size();i++){double[] a=normalized.get(i),z=normalized.get(normalized.size()-1-i);int cmp=Double.compare(a[0],z[0]);if(cmp==0)cmp=Double.compare(a[1],z[1]);if(cmp!=0){reverse=cmp>0;break;}}
        JSONArray output=new JSONArray();for(int i=0;i<normalized.size();i++){double[] c=normalized.get(reverse?normalized.size()-1-i:i);output.put(new JSONArray().put(c[0]).put(c[1]));}return digest(output);
    }
    static String canonical(Object value) {
        if(value==null||value==JSONObject.NULL)return "null";
        if(value instanceof JSONObject) {
            JSONObject object=(JSONObject)value;TreeSet<String> keys=new TreeSet<>();object.keys().forEachRemaining(keys::add);
            StringBuilder out=new StringBuilder("{");for(String k:keys){if(out.length()>1)out.append(',');out.append(quote(k)).append(':').append(canonical(object.opt(k)));}return out.append('}').toString();
        }
        if(value instanceof JSONArray) {JSONArray a=(JSONArray)value;StringBuilder out=new StringBuilder("[");for(int i=0;i<a.length();i++){if(i>0)out.append(',');out.append(canonical(a.opt(i)));}return out.append(']').toString();}
        if(value instanceof String)return quote((String)value);
        if(value instanceof Boolean)return value.toString();
        if(value instanceof Number) {
            if(value instanceof Double||value instanceof Float) {
                double n=((Number)value).doubleValue();if(!Double.isFinite(n))throw new IllegalArgumentException("Nonfinite number");
                if(n==0)return Double.doubleToRawLongBits(n)<0?"-0.0":"0.0";
                BigDecimal d=BigDecimal.valueOf(n).stripTrailingZeros();int exponent=d.precision()-d.scale()-1;
                if(exponent>=-4&&exponent<16){String plain=d.toPlainString();return plain.contains(".")?plain:plain+".0";}
                String mantissa=d.movePointLeft(exponent).toPlainString();return mantissa+"e"+(exponent>=0?"+":"-")+String.format(java.util.Locale.ROOT,"%02d",Math.abs(exponent));
            }
            return value.toString();
        }
        throw new IllegalArgumentException("Unsupported evidence value");
    }
    private static String quote(String text) {
        StringBuilder out=new StringBuilder("\"");for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);switch(c){case '"':out.append("\\\"");break;case '\\':out.append("\\\\");break;
                case '\b':out.append("\\b");break;case '\f':out.append("\\f");break;case '\n':out.append("\\n");break;case '\r':out.append("\\r");break;case '\t':out.append("\\t");break;
                default:if(c<32||c>126)out.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c));else out.append(c);}
        }return out.append('"').toString();
    }
    static int integer(Object v) {if(!(v instanceof Integer)&&!(v instanceof Long))throw new IllegalArgumentException("Integer required");long n=((Number)v).longValue();if(n<Integer.MIN_VALUE||n>Integer.MAX_VALUE)throw new IllegalArgumentException("Index outside range");return (int)n;}
    static long node(Object v) {if(!(v instanceof Number)||v instanceof Boolean)throw new IllegalArgumentException("Node required");double n=((Number)v).doubleValue();if(!Double.isFinite(n)||n<=0||n>=9007199254740992d||n!=Math.rint(n))throw new IllegalArgumentException("Invalid node");return (long)n;}
    static boolean signed(JSONObject object) throws Exception {
        JSONObject copy=new JSONObject(object.toString());String expected=copy.getString("content_sha256");copy.remove("content_sha256");return digest(copy).equals(expected);
    }
}
