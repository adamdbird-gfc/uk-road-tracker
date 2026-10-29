package com.roadprints.capture;

/** Persistent-budget arithmetic kept independent of Android and network I/O. */
final class ContextRequestPolicy {
    static final int DAILY_LIMIT=48;
    static boolean allowed(long now,long day,int requests,long nextAllowed) {
        return now>=nextAllowed && (day!=now/86400000L || requests<DAILY_LIMIT);
    }
    static long failureDelay(int failures,long retryAfterSeconds) {
        return Math.max(Math.min(14400000L,600000L*(1L<<Math.min(5,Math.max(0,failures)))),
                Math.min(86400000L,Math.max(0,retryAfterSeconds)*1000L));
    }
}
