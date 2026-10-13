package com.roadprints.capture;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
/** Timed, accurate stationary evidence shared by every venue catalogue. */
final class VenueStopDetector {
    static final class Stop {
        final String id;final long first,last;final double lat,lng,accuracy;
        Stop(String id,long first,long last,double lat,double lng,double accuracy){this.id=id;this.first=first;this.last=last;this.lat=lat;this.lng=lng;this.accuracy=accuracy;}
    }
    static List<Stop> stops(JSONArray samples,JSONArray venues){
        List<Stop> out=new ArrayList<>();if(samples==null||venues==null)return out;
        String active="";long first=0,previous=0;int count=0;double lat0=0,lng0=0,worst=0;boolean added=false;
        for(int i=0;i<samples.length();i++){
            JSONArray p=samples.optJSONArray(i);double lng=p==null?Double.NaN:p.optDouble(0,Double.NaN),lat=p==null?Double.NaN:p.optDouble(1,Double.NaN);
            double accuracy=p==null?Double.NaN:p.optDouble(2,Double.NaN),speed=p==null?Double.NaN:p.optDouble(4,Double.NaN);long time=p==null?0:p.optLong(3);
            if(!Double.isFinite(lat)||!Double.isFinite(lng)||Math.abs(lat)>90||Math.abs(lng)>180||!Double.isFinite(accuracy)||accuracy<=0||accuracy>50||!Double.isFinite(speed)||speed>2||time<=0){active="";count=0;previous=0;continue;}
            if(time==previous)continue;
            JSONObject venue=ServiceStationEvidence.nearest(venues,lat,lng,125);
            if(venue==null){active="";count=0;previous=0;continue;}
            String id=venue.optString("id");
            if(!id.equals(active)||time<previous||time-previous>90000||ServiceStationEvidence.metres(lat,lng,lat0,lng0)>80){active=id;first=time;count=1;lat0=lat;lng0=lng;worst=accuracy;added=false;}
            else {count++;worst=Math.max(worst,accuracy);}
            previous=time;
            if(!id.isEmpty()&&count>=4&&time-first>=180000&&!added){out.add(new Stop(id,first,time,lat0,lng0,worst));added=true;}
            else if(added)out.set(out.size()-1,new Stop(id,first,time,lat0,lng0,worst));
        }return out;
    }
}
