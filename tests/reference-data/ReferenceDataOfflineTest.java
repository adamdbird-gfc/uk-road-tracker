package com.roadprints.capture;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Synthetic payloads only; the URL factory rejects every unplanned connection. */
public final class ReferenceDataOfflineTest {
    private static int passed;
    private static final byte[] V1 = "synthetic-reference-one".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final byte[] V2 = "synthetic-reference-two".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final ReferenceDataCache.Validator VALIDATE = bytes -> {
        if (!Arrays.equals(bytes, V1) && !Arrays.equals(bytes, V2)) throw new IOException("Invalid fixture");
    };
    private static void check(boolean result) { if (!result) throw new AssertionError(); }
    private static void pass(String name) { passed++; System.out.println("PASS " + name); }
    private static void fails(Callable<?> call) throws Exception {
        try { call.call(); } catch (IOException expected) { return; }
        throw new AssertionError("Expected failure");
    }
    private static ReferenceDataCache.Loader load(AtomicInteger count, byte[] bytes) {
        return () -> { count.incrementAndGet(); return bytes; };
    }
    public static void main(String[] args) throws Exception {
        // Every URL connection in this JVM is either this explicitly mocked transport or rejected.
        URL.setURLStreamHandlerFactory(protocol -> new URLStreamHandler() {
            protected URLConnection openConnection(URL url) throws IOException {
                if (!url.getHost().equals("synthetic.invalid")) throw new IOException("Unexpected network forbidden");
                return new HttpURLConnection(url) {
                    public void connect() { }
                    public void disconnect() { }
                    public boolean usingProxy() { return false; }
                    public int getResponseCode() { return url.getPath().equals("/failure") ? 503 : 200; }
                    public InputStream getInputStream() { return new ByteArrayInputStream(V1); }
                };
            }
        });
        Path base = Files.createTempDirectory("reference-offline-");
        try {
            ReferenceDataDiagnostics.initialize(base.toFile());
            AtomicInteger count = new AtomicInteger();
            ReferenceDataCache cache = new ReferenceDataCache(base.resolve("cache").toFile());
            check(Arrays.equals(V1, cache.get("boundary", "v1", load(count,V1), VALIDATE)) && count.get()==1);
            pass("first installation requests once");
            cache = new ReferenceDataCache(base.resolve("cache").toFile());
            cache.get("boundary", "v1", load(count,V1), VALIDATE); check(count.get()==1);
            pass("new cache instance / process-style restart reuses persistent data");
            for (String scenario : new String[]{"Map Progress Journeys navigation", "focused map reopen", "collection navigation", "layer switches", "app upgrade unchanged pin", "journey recalculation"}) {
                for (int i=0;i<10;i++) cache.get("boundary", "v1", load(count,V1), VALIDATE);
                check(count.get()==1); pass(scenario+" model: unchanged reference makes zero new requests");
            }
            cache.get("boundary", "v2", load(count,V2), VALIDATE); check(count.get()==2);
            pass("changed dataset version requests once");
            final ReferenceDataCache shared = cache;
            check(Arrays.equals(V2, cache.get("boundary", "v2", ()->{throw new IOException("offline");},VALIDATE)));
            pass("offline startup with valid cache");
            byte[] fallback=cache.get("boundary","v3",()->{count.incrementAndGet();throw new InterruptedIOException();},VALIDATE);
            check(Arrays.equals(V2,fallback) && count.get()==3);
            cache.get("boundary","v3",load(count,V1),VALIDATE);check(count.get()==3);
            pass("interrupted update retains previous generation and defers retry");
            Files.write(base.resolve("cache/boundary.ref"), new byte[]{1,2,3});
            Files.deleteIfExists(base.resolve("cache/boundary.retry"));
            cache.get("boundary","v2",load(count,V2),VALIDATE);check(count.get()==4);
            pass("corrupt local cache recovered by one validated replacement");
            ExecutorService pool=Executors.newFixedThreadPool(8);
            try {
                List<Future<byte[]>> jobs=new ArrayList<>();
                for(int i=0;i<16;i++) jobs.add(pool.submit(()->shared.get("concurrent","v1",load(count,V1),VALIDATE)));
                for(Future<byte[]> job:jobs)check(Arrays.equals(job.get(),V1));
                check(count.get()==5);pass("16 simultaneous requests share one download");
            } finally { pool.shutdownNow(); }
            AtomicInteger failures=new AtomicInteger();
            ReferenceDataCache.Loader broken=()->{failures.incrementAndGet();throw new IOException("offline");};
            for(int i=0;i<20;i++)fails(()->shared.get("unavailable","v1",broken,VALIDATE));
            check(failures.get()==1);pass("failed request has no automatic retry loop");
            fails(()->shared.get("invalid","v1",()->"invalid".getBytes(),VALIDATE));
            check(!Files.exists(base.resolve("cache/invalid.ref")));pass("invalid download never committed");
            Thread.currentThread().interrupt();
            fails(()->shared.get("cancelled","v1",load(count,V1),VALIDATE));Thread.interrupted();check(count.get()==5);
            pass("cancelled consumer does not start a download");
            AtomicInteger retried = new AtomicInteger();
            shared.get("cancelled","v1",load(retried,V1),VALIDATE);
            check(retried.get()==1);pass("pre-cancelled request does not poison later retry");
            shared.get("backoff-write-failure","v1",()->V1,VALIDATE);
            Files.createDirectory(base.resolve("cache/backoff-write-failure.retry"));
            check(Arrays.equals(V1,shared.get("backoff-write-failure","v2",()->{throw new IOException("failed");},VALIDATE)));
            pass("backoff metadata write failure preserves valid fallback");
            ExecutorService independent=Executors.newFixedThreadPool(2);
            CountDownLatch loading=new CountDownLatch(1),release=new CountDownLatch(1);
            try {
                String prefix=base.resolve("cache").toFile().getCanonicalPath()+"/";
                String key="independent";
                while (((prefix+key).hashCode()&Integer.MAX_VALUE)%32==((prefix+"blocked").hashCode()&Integer.MAX_VALUE)%32) key+="x";
                Future<byte[]> blocked=independent.submit(()->shared.get("blocked","v1",()->{loading.countDown();release.await();return V1;},VALIDATE));
                check(loading.await(2,TimeUnit.SECONDS));
                final String otherKey=key;
                check(Arrays.equals(V1,independent.submit(()->shared.get(otherKey,"v1",()->V1,VALIDATE)).get(2,TimeUnit.SECONDS)));
                release.countDown();check(Arrays.equals(V1,blocked.get(2,TimeUnit.SECONDS)));
                pass("unrelated reference keys do not queue behind a blocked download");
            } finally { release.countDown();independent.shutdownNow(); }
            System.setProperty("roadprints.referenceNetworkDisabled","true");
            fails(()->new ReferenceDataConnection(new URL("https://synthetic.invalid/blocked"),"ncn","v1","test"));
            System.clearProperty("roadprints.referenceNetworkDisabled");
            pass("offline test property rejects reference connection before transport creation");
            ReferenceDataConnection connection=new ReferenceDataConnection(new URL("https://synthetic.invalid/ok"),"ncn","v1","synthetic_test");
            check(connection.getResponseCode()==200);
            try(InputStream input=connection.getInputStream()){check(Arrays.equals(input.readAllBytes(),V1));}
            connection.disconnect();connection.disconnect();
            check(ReferenceDataDiagnostics.report().contains("ncn.bytes="+V1.length));
            check(ReferenceDataDiagnostics.report().contains("ncn.requests=1"));pass("transport counts consumed bytes once");
            ReferenceDataConnection failed=new ReferenceDataConnection(new URL("https://synthetic.invalid/failure"),"ncn","v1","synthetic_test");
            check(failed.getResponseCode()==503);failed.disconnect();
            check(ReferenceDataDiagnostics.report().contains("failures=1"));pass("failed HTTP status recorded");
            fails(()->new ReferenceDataConnection(new URL("https://unexpected.invalid/blocked"),"ncn","v1","test"));
            pass("unexpected connection rejected before network");
            for(int i=0;i<100;i++)ReferenceDataDiagnostics.cache("ncn","v1","v1",true,"valid");
            ReferenceDataDiagnostics.flush();
            Properties stored=new Properties();try(InputStream in=Files.newInputStream(base.resolve("reference-bandwidth.properties"))){stored.load(in);}
            check(stored.getProperty("recent").split("\n").length==64);pass("diagnostics retain 64 events and aggregate counters");
            ReferenceDataDiagnostics.clear();
            check(ReferenceDataDiagnostics.report().isEmpty());
            ReferenceDataDiagnostics.flush(); // queued/background flush must not resurrect the old ledger.
            stored.clear();try(InputStream in=Files.newInputStream(base.resolve("reference-bandwidth.properties"))){stored.load(in);}
            check(stored.isEmpty());
            check(Files.exists(base.resolve("cache/boundary.ref")));
            pass("clearing diagnostics empties memory and disk without deleting reference data");
            System.out.println(passed+" offline checks passed; no real network transport available");
        } finally {
            try(java.util.stream.Stream<Path> paths=Files.walk(base)) {
                for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);
            }
        }
    }
}
