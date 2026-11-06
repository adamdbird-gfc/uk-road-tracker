package com.roadprints.capture;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** One atomic presentation record per user. Never opens the ledger or entitlement preferences. */
final class MilepostPresentationStore {
    private final SharedPreferences prefs;
    private final String key;
    static final class State {
        long acknowledgedPosts,acknowledgedAwards;
        String acknowledgedReceipt="",dismissedReceipt="";
        int completedStage,cursor,artworkVersion=MilepostPresentation.ARTWORK_VERSION;
        boolean paused=true,dismissed,replaying,staticMode=true;
    }
    MilepostPresentationStore(Context context,String user){
        prefs=context.getSharedPreferences("roadprints_milepost_presentation_v1",Context.MODE_PRIVATE);
        key="user:"+user;
    }
    State read(){
        State s=new State();String raw=prefs.getString(key,null);if(raw==null)return s;
        try {
            JSONObject j=new JSONObject(raw);
            if(j.getInt("schema")!=1)throw new IllegalStateException("Unsupported presentation schema");
            s.acknowledgedPosts=Math.max(0,j.getLong("posts"));s.acknowledgedAwards=Math.max(0,j.getLong("awards"));
            s.acknowledgedReceipt=j.getString("receipt");s.dismissedReceipt=j.optString("dismissed_receipt","");s.completedStage=MilepostPresentation.stage(j.getInt("stage"));
            s.cursor=MilepostPresentation.stage(j.getInt("cursor"));s.artworkVersion=j.getInt("artwork");
            s.paused=j.getBoolean("paused");s.dismissed=j.getBoolean("dismissed");s.replaying=j.getBoolean("replaying");s.staticMode=j.getBoolean("static");
            return s;
        }catch(Exception corrupt){throw new IllegalStateException("Presentation state unavailable; original record retained",corrupt);}
    }
    void save(State s){
        try {
            JSONObject j=new JSONObject().put("schema",1).put("posts",s.acknowledgedPosts).put("awards",s.acknowledgedAwards)
                .put("dismissed_receipt",s.dismissedReceipt).put("receipt",s.acknowledgedReceipt).put("stage",s.completedStage).put("cursor",s.cursor).put("artwork",s.artworkVersion)
                .put("paused",s.paused).put("dismissed",s.dismissed).put("replaying",s.replaying).put("static",s.staticMode);
            if(!prefs.edit().putString(key,j.toString()).commit())throw new IllegalStateException("Presentation state could not be saved");
        }catch(org.json.JSONException invalid){throw new IllegalStateException(invalid);}
    }
    /** Only the supplied complete batch is acknowledged. Discoveries arriving later remain pending. */
    void acknowledge(State s,MilepostPresentation.Snapshot batch){
        if(!batch.current)return;
        s.acknowledgedPosts=Math.max(s.acknowledgedPosts,batch.postOrdinal);
        s.acknowledgedAwards=Math.max(s.acknowledgedAwards,batch.awardOrdinal);
        s.acknowledgedReceipt=batch.receipt;save(s);
    }
    boolean pending(State s,MilepostPresentation.Snapshot batch){
        return batch.current&&(batch.postOrdinal>s.acknowledgedPosts||batch.awardOrdinal>s.acknowledgedAwards
                ||((batch.pending+batch.incomplete)>0&&!batch.receipt.equals(s.acknowledgedReceipt)));
    }
}
