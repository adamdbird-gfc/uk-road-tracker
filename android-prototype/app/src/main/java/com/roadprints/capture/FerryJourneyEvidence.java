package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;

/** Conservative offline Dover–Calais corridor v1. This is NOT a global water mask.
 * Port context alone, a missing GPS interval, or one offshore fix never qualifies.
 * No inference is made for the Channel Tunnel (south/west of the marine gate).
 * Boundaries are measured terminal transitions, not asserted berth departure times. */
final class FerryJourneyEvidence {
    static final class Review {
        int start=-1,end=-1,marineFixes; long marineSince,marineUntil; double marineDistance;
        String reason="No supported ferry interval in this recording. Missing travel cannot be reconstructed from a GPS gap.";
        boolean hasMarineEvidence(){return marineFixes>=4&&marineUntil-marineSince>=180_000L&&marineDistance>=1500;}
        boolean canSplit(){return hasMarineEvidence()&&start>=0&&end>start;}
        JSONObject json() throws Exception {
            return new JSONObject().put("version",1).put("scope","dover_calais_corridor")
                    .put("start_index",start).put("end_index",end).put("marine_fixes",marineFixes)
                    .put("marine_duration_ms",Math.max(0,marineUntil-marineSince)).put("marine_distance_m",marineDistance)
                    .put("confidence",canSplit()?"supported_terminal_transition":hasMarineEvidence()?"partial_requires_review":"insufficient")
                    .put("reason",reason).put("original_geometry_preserved",true);
        }
    }
    /** Mode, names and port proximity alone never make a crossing reviewable. */
    static boolean reviewAvailable(JSONObject journey) {
        if(journey==null)return false;
        JSONObject recovery=journey.optJSONObject("ferry_recovery");
        if(recovery!=null&&!recovery.optString("backup_key").isEmpty()
                &&!recovery.optString("source_id").isEmpty())return true;
        return inspect(journey.optJSONArray("capture_route_samples")).hasMarineEvidence();
    }
    static int terminal(double lon,double lat) {
        if(RailStationCatalog.metres(lat,lon,51.126,1.333)<2500)return 1;
        if(RailStationCatalog.metres(lat,lon,50.981,1.875)<2500)return 2;
        return 0;
    }
    static boolean marine(double lon,double lat) {
        // Deliberately well offshore of both coasts, distinct from the tunnel alignment.
        return lon>=1.48&&lon<=1.72&&lat>=51.035&&lat<=51.13;
    }
    static Review inspect(JSONArray samples) {
        Review out=new Review();if(samples==null)return out;
        int origin=0,lastPort=-1,arrival=-1;JSONArray previousMarine=null;long lastTime=0;
        for(int i=0;i<samples.length();i++) {
            JSONArray p=samples.optJSONArray(i);
            if(p==null||p.length()<5)continue;
            double lon=p.optDouble(0),lat=p.optDouble(1),accuracy=p.optDouble(2),speed=p.optDouble(4,-1);
            long time=p.optLong(3);
            if(!Double.isFinite(lon)||!Double.isFinite(lat)||!Double.isFinite(accuracy)||accuracy<0||accuracy>35||time<=lastTime)continue;
            lastTime=time;int port=terminal(lon,lat);
            if(!out.hasMarineEvidence() && port!=0) {origin=port;lastPort=i;}
            if(marine(lon,lat)&&speed>=2&&speed<=20) {
                if(previousMarine!=null) {
                    long gap=time-previousMarine.optLong(3);
                    double d=RailStationCatalog.metres(lat,lon,previousMarine.optDouble(1),previousMarine.optDouble(0));
                    if(gap>600_000L||d>20*(gap/1000.0)+100) {
                        out.marineFixes=0;out.marineSince=0;out.marineDistance=0;previousMarine=null;
                    } else out.marineDistance+=d;
                }
                if(out.marineFixes++==0)out.marineSince=time;
                out.marineUntil=time;previousMarine=p;
                if(out.hasMarineEvidence()&&out.start<0)out.start=lastPort;
            }
            if(out.hasMarineEvidence()&&origin!=0&&port!=0&&port!=origin)arrival=i;
            if(arrival>=0&&port==0&&i>arrival && time-samples.optJSONArray(arrival).optLong(3)>=30_000L) {
                out.end=arrival;out.reason="Sustained marine movement between distinct terminals; review the measured terminal transitions.";break;
            }
        }
        if(out.hasMarineEvidence()&&!out.canSplit())out.reason="Marine movement recorded, but a terminal transition is missing; review without inventing a boundary.";
        return out;
    }
}
