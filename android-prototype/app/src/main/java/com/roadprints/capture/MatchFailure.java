package com.roadprints.capture;

/** Bounded, coordinate-free matcher failure classification. */
final class MatchFailure extends java.io.IOException {
    final int status;
    MatchFailure(int status,String message){super(message);this.status=status;}
    static String category(Throwable error) {
        for(Throwable e=error;e!=null;e=e.getCause()) {
            if(e instanceof java.net.UnknownHostException)return "dns";
            if(e instanceof java.net.SocketTimeoutException)return "network_timeout";
            if(e instanceof java.net.ConnectException || e instanceof java.net.SocketException)return "connection";
            if(e instanceof MatchFailure) {
                int status=((MatchFailure)e).status;
                return status==504?"backend_timeout":status==429?"rate_limited":status>=500?"backend_unavailable":"request_rejected";
            }
        }
        String message=String.valueOf(error.getMessage()).toLowerCase(java.util.Locale.ROOT);
        if(message.contains("distance") || message.contains("gps evidence"))return "distance_integrity";
        if(message.contains("ferry"))return "transport_review";
        return "evidence_rejected";
    }
    static boolean retryable(Throwable error) {
        String category=category(error);
        return category.equals("dns")||category.equals("connection")||category.equals("network_timeout")
                ||category.equals("backend_timeout")||category.equals("backend_unavailable")||category.equals("rate_limited");
    }
    static boolean retryNow(Throwable error,int attempt) {
        // A 55-second backend budget failure needs a later user retry, not four identical workloads.
        return attempt==0 && retryable(error) && !category(error).equals("backend_timeout")
                && !category(error).equals("rate_limited");
    }
}
