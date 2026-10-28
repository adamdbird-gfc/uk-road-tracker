package com.roadprints.capture;

import java.io.*;
import java.net.*;

/** Counts only bytes actually consumed; opening this wrapper does not connect. */
final class ReferenceDataConnection extends HttpURLConnection {
    private final HttpURLConnection delegate;
    private final String dataset, version, trigger, cachedVersion;
    private long started, bytes;
    private boolean attempted, complete, recorded;
    private int code = -1;
    ReferenceDataConnection(URL url, String dataset, String version, String trigger) throws IOException {
        this(url, dataset, version, trigger, "unknown_see_cache_event");
    }
    ReferenceDataConnection(URL url, String dataset, String version, String trigger, String cachedVersion) throws IOException {
        super(url);
        if (Boolean.getBoolean("roadprints.referenceNetworkDisabled"))
            throw new IOException("Reference network disabled for offline verification");
        delegate = (HttpURLConnection) url.openConnection();
        this.dataset = dataset; this.version = version; this.trigger = trigger; this.cachedVersion = cachedVersion;
    }
    private void begin() { if (!attempted) { attempted = true; started = System.nanoTime(); } }
    public int getResponseCode() throws IOException { begin(); return code = delegate.getResponseCode(); }
    public InputStream getInputStream() throws IOException {
        begin();
        return new FilterInputStream(delegate.getInputStream()) {
            public int read() throws IOException { int n = in.read(); if (n >= 0) bytes++; else complete = true; return n; }
            public int read(byte[] b, int off, int len) throws IOException {
                int n = in.read(b, off, len); if (n > 0) bytes += n; else if (n == -1) complete = true; return n;
            }
        };
    }
    public void disconnect() {
        try { delegate.disconnect(); } finally {
            if (attempted && !recorded) { recorded = true;
                ReferenceDataDiagnostics.network(dataset, version, cachedVersion, trigger, bytes, (System.nanoTime() - started) / 1_000_000,
                        code >= 200 && code < 300 && complete);
            }
        }
    }
    public void connect() throws IOException { begin(); delegate.connect(); }
    public boolean usingProxy() { return delegate.usingProxy(); }
    public void setConnectTimeout(int value) { delegate.setConnectTimeout(value); }
    public void setReadTimeout(int value) { delegate.setReadTimeout(value); }
    public void setInstanceFollowRedirects(boolean value) { delegate.setInstanceFollowRedirects(value); }
    public void setRequestProperty(String key, String value) { delegate.setRequestProperty(key, value); }
    public String getHeaderField(String key) { return delegate.getHeaderField(key); }
    public long getContentLengthLong() { return delegate.getContentLengthLong(); }
}
