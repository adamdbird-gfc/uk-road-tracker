package com.roadprints.capture;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.time.Instant;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Explicit worker-only adapter over a Stage 5 receipt. No scheduling, derivation or geometry loading. */
final class MilepostPresentationReader {
    static MilepostPresentation.Snapshot read(Context context,MilepostDiscoveryLedger ledger,String user,
                                               long acknowledgedPosts,BooleanSupplier cancelled)throws Exception {
        SQLiteDatabase d=ledger.db(); // Existing ledger rejects main-thread reads.
        MilepostArchiveReader.check(cancelled);
        d.beginTransaction();
        try {
            JSONObject state=MilepostAchievementDeriver.state(context,ledger,user);
            Map<String,JSONObject> byId=new HashMap<>();JSONArray rows=state.getJSONArray("rows");
            for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);byId.put(row.getString("id"),row);}
            List<MilepostPresentation.Award> awards=new ArrayList<>();long awardOrdinal=0;
            for(String id:MilepostAchievements.IDS){
                JSONObject row=byId.get(id);
                MilepostPresentation.Award a=row==null?new MilepostPresentation.Award(false,0,null,"unknown"):
                    new MilepostPresentation.Award(row.getBoolean("earned"),row.optLong("ordinal",0),
                        row.isNull("event_date")?null:row.getString("event_date"),row.getString("precision"));
                awards.add(a);if(a.earned)awardOrdinal=Math.max(awardOrdinal,a.ordinal);
            }
            int count=(int)MilepostAchievements.number(d,"SELECT discovery_count FROM achievement_snapshot WHERE user=?",user);
            int pending=(int)MilepostAchievements.number(d,"SELECT COUNT(*) FROM journey WHERE user=? AND deleted=0 AND state='QUEUED'",user);
            int incomplete=(int)MilepostAchievements.number(d,"SELECT COUNT(*) FROM journey WHERE user=? AND deleted=0 AND state IN ('PENDING_EVIDENCE','PENDING_CATALOGUE')",user);
            boolean current=state.getBoolean("current");
            int newPosts=0;long postOrdinal=0;String from=null,to=null,after="";
            if(current)for(;;){
                MilepostArchiveReader.check(cancelled);int read=0;
                try(Cursor c=MilepostAchievements.factPage(d,user,after)){
                    while(c.moveToNext()){
                        MilepostArchiveReader.check(cancelled);
                        MilepostAchievements.Fact f=new MilepostAchievements.Fact(c);after=f.marker;read++;
                        long ordinal=MilepostAchievements.number(d,"SELECT ordinal FROM recognition WHERE user=? AND marker=?",user,f.marker);
                        postOrdinal=Math.max(postOrdinal,ordinal);
                        if(ordinal>acknowledgedPosts){
                            newPosts++;
                            // Display calendar dates only. Journey starts do not establish exact passage times.
                            if(f.date!=null){String date=Instant.parse(f.date).atOffset(java.time.ZoneOffset.UTC).toLocalDate().toString();
                                if(from==null||date.compareTo(from)<0)from=date;if(to==null||date.compareTo(to)>0)to=date;}
                        }
                    }
                }
                if(read<16)break;
            }
            String receipt=MilepostAchievements.scalar(d,"SELECT catalogue||':'||ledger_generation||':'||ncn_token FROM achievement_snapshot WHERE user=?",user);
            // Publication/capture may have changed while compact pages were read. Never announce a stale batch.
            current&=MilepostAchievementDeriver.state(context,ledger,user).getBoolean("current");
            return new MilepostPresentation.Snapshot(current,count,current?newPosts:0,pending,incomplete,postOrdinal,awardOrdinal,
                receipt==null?"":receipt,from,to,awards);
        }finally{d.endTransaction();}
    }
    private MilepostPresentationReader(){}
}
