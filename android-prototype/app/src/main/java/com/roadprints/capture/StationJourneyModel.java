package com.roadprints.capture;

/** Evidence-based station boundaries. All indices refer to the retained GPS capture. */
final class StationJourneyModel {
    static final class Decision {
        final int endIndex, startIndex;
        final String nextMode, stationCode, reason;
        Decision(int endIndex, int startIndex, String nextMode, String stationCode, String reason) {
            this.endIndex=endIndex; this.startIndex=startIndex; this.nextMode=nextMode;
            this.stationCode=stationCode; this.reason=reason;
        }
    }
    private final RailStationCatalog catalog;
    private RailStationCatalog.Station station;
    private int arrivalIndex=-1, departureIndex=-1, walkIndex=-1;
    private long arrivalTime, activitySince, fastSince, lastTime;
    private String activity="unknown";
    private boolean stopEvidence;
    private double walkMetres, lastLat, lastLon;
    private float lastAccuracy;
    private int walkingSamples;

    StationJourneyModel(RailStationCatalog catalog) { this.catalog=catalog; }
    void activity(String value, long time, int pointIndex) {
        // Repeated subscription callbacks must not restart a stop or walking timer.
        if (!value.equals(activity)) { activity=value; activitySince=time; }
        if (station!=null && ("still".equals(value) || "vehicle".equals(value))) stopEvidence=true;
        if (station!=null && "walking".equals(value) && walkIndex<0) {
            walkIndex=Math.max(arrivalIndex,pointIndex); walkMetres=0; walkingSamples=0;
        }
        if (!"walking".equals(value)) { walkIndex=-1; walkMetres=0; walkingSamples=0; }
    }
    Decision sample(String mode, int index, long time, double lat, double lon, float accuracy, float speed) {
        if (!Float.isFinite(accuracy) || accuracy<=0 || accuracy>80 || time<=lastTime) return null;
        RailStationCatalog.Station nearby=catalog.nearest(lat,lon,accuracy);
        double step=lastTime==0 ? 0 : RailStationCatalog.metres(lastLat,lastLon,lat,lon);
        long gap=time-lastTime;
        if (!Float.isFinite(speed) || speed<0) speed=gap>0 && gap<=120000 ? (float)(step*1000/gap) : 0;
        // Large jumps are not walking evidence even when GPS claims good accuracy.
        if ("walking".equals(activity) && walkIndex>=0 && speed<=3 && gap>0 && gap<=60000 && step<=100) {
            walkMetres+=Math.max(0,step-(lastAccuracy+accuracy)*.35); walkingSamples++;
        }
        lastTime=time; lastLat=lat; lastLon=lon; lastAccuracy=accuracy;
        if (!"walking".equals(mode) && !"unknown".equals(mode) && !"train".equals(mode)) return null;
        if (nearby!=null && speed<=3 && (station==null || !nearby.code.equals(station.code))) {
            station=nearby; arrivalIndex=index; arrivalTime=time; stopEvidence="still".equals(activity);
            fastSince=0; departureIndex=-1; walkIndex="walking".equals(activity)?index:-1; walkMetres=0; walkingSamples=0;
        }
        if (station==null) return null;
        // A train stop, even a long one, is not an arrival unless reliable walking follows.
        if ("train".equals(mode) && "walking".equals(activity) && walkIndex>=0
                && time-activitySince>=30000 && walkingSamples>=3 && walkMetres>=35) {
            return new Decision(Math.max(1,walkIndex),Math.max(1,walkIndex),"walking",station.code,"station_alighting_or_transfer");
        }
        double fromStation=RailStationCatalog.metres(lat,lon,station.latitude,station.longitude);
        if (speed>=6 && gap<=120000) {
            if (fastSince==0) { fastSince=time; departureIndex=index; }
        } else { fastSince=0; departureIndex=-1; }
        if (fastSince!=0 && time-fastSince>=30000 && fromStation>station.radius+400
                && stopEvidence && "vehicle".equals(activity) && time-activitySince>=15000
                && time-arrivalTime>=60000 && !"train".equals(mode)) {
            if ("walking".equals(mode))
                return new Decision(Math.max(1,arrivalIndex),Math.max(1,departureIndex),"train",station.code,"station_boarding_departure");
            // Origin, dwell, activity and sustained departure support a train suggestion.
            // Existing transport confirmation remains required, including possible car/bus departures.
            return new Decision(-1,-1,"train",station.code,"station_departure_train_suggestion");
        }
        // Walking past the station must not leave a stale boarding candidate behind.
        if (!"train".equals(mode) && fromStation>station.radius+200 && speed<3 && fastSince==0) clearStation();
        // Through trains discard the old stop evidence once they leave its area.
        if ("train".equals(mode) && fromStation>station.radius+400 && speed>=6) clearStation();
        return null;
    }
    boolean holdStill(String mode, long now) {
        return "train".equals(mode) || (station!=null && stopEvidence && now-arrivalTime<45*60000L);
    }
    boolean ownsModeChange(String mode) {
        return "train".equals(mode) || (station!=null && stopEvidence && "walking".equals(mode));
    }
    void clearStation() {
        station=null; arrivalIndex=-1; arrivalTime=0; stopEvidence=false;
        departureIndex=-1; fastSince=0; walkIndex=-1; walkMetres=0; walkingSamples=0;
    }
    int stationaryEndIndex(String mode) { return "walking".equals(mode) && stopEvidence ? arrivalIndex : -1; }
    String currentActivity() { return activity; }
    String[] checkpoint() {
        return new String[]{stationCode(),String.valueOf(arrivalIndex),String.valueOf(arrivalTime),
                String.valueOf(departureIndex),String.valueOf(walkIndex),String.valueOf(activitySince),
                String.valueOf(fastSince),String.valueOf(lastTime),activity,String.valueOf(stopEvidence),
                String.valueOf(walkMetres),String.valueOf(lastLat),String.valueOf(lastLon),
                String.valueOf(lastAccuracy),String.valueOf(walkingSamples)};
    }
    void restore(String[] state, int pointCount) {
        if (state.length!=15) return;
        try {
            int arrival=Integer.parseInt(state[1]), walk=Integer.parseInt(state[4]);
            if (arrival>=pointCount || walk>=pointCount) return;
            for (RailStationCatalog.Station candidate:catalog.stations)
                if (candidate.code.equals(state[0])) { station=candidate; break; }
            arrivalIndex=arrival; arrivalTime=Long.parseLong(state[2]);
            departureIndex=Integer.parseInt(state[3]); walkIndex=walk; activitySince=Long.parseLong(state[5]);
            fastSince=Long.parseLong(state[6]); lastTime=Long.parseLong(state[7]); activity=state[8];
            stopEvidence=Boolean.parseBoolean(state[9]); walkMetres=Double.parseDouble(state[10]);
            lastLat=Double.parseDouble(state[11]); lastLon=Double.parseDouble(state[12]);
            lastAccuracy=Float.parseFloat(state[13]); walkingSamples=Integer.parseInt(state[14]);
        } catch (RuntimeException ignored) { clearStation(); }
    }
    String stationCode() { return station==null ? "" : station.code; }
}
