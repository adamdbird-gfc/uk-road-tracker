package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.TreeSet;

/** Stable across archive JSON round trips; streams instead of copying full geometry. */
final class MapEvidenceFingerprint {
    private MapEvidenceFingerprint() {}
    static String digest(Object value) {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            append(hash, value);
            StringBuilder out = new StringBuilder(64);
            for (byte b : hash.digest()) {
                out.append(Character.forDigit((b & 255) >>> 4, 16));
                out.append(Character.forDigit(b & 15, 16));
            }
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
    private static void text(MessageDigest hash, String value) {
        hash.update(value.getBytes(StandardCharsets.UTF_8));
    }
    private static void append(MessageDigest hash, Object value) {
        if (value == null || value == JSONObject.NULL) { text(hash, "null"); return; }
        if (value instanceof JSONObject object) {
            TreeSet<String> keys = new TreeSet<>();
            object.keys().forEachRemaining(keys::add);
            text(hash, "{"); boolean first = true;
            for (String key : keys) {
                if (!first) text(hash, ","); first = false;
                text(hash, JSONObject.quote(key)); text(hash, ":"); append(hash, object.opt(key));
            }
            text(hash, "}");
        } else if (value instanceof JSONArray array) {
            text(hash, "[");
            for (int i = 0; i < array.length(); i++) {
                if (i > 0) text(hash, ",");
                append(hash, array.opt(i));
            }
            text(hash, "]");
        } else if (value instanceof Number number) {
            // Android JSON writes 0.0 as 0. Evidence geometry is numerically identical.
            text(hash, new BigDecimal(number.toString()).stripTrailingZeros().toPlainString());
        } else if (value instanceof Boolean) text(hash, value.toString());
        else if (value instanceof String string) text(hash, JSONObject.quote(string));
        else throw new IllegalArgumentException("Unsupported Map evidence value");
    }
}
