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
                try(Cursor c=MilepostAchievements.factPage(d,user,after,true)){
                    while(c.moveToNext()){
                        MilepostArchiveReader.check(cancelled);
                        MilepostAchievements.Fact f=new MilepostAchievements.Fact(c);after=f.marker;read++;
                        long ordinal=c.isNull(8)?0:c.getLong(8);
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
    static final class Discovery {
        final String marker,date;final long ordinal;
        Discovery(String marker,String date,long ordinal){this.marker=marker;this.date=date;this.ordinal=ordinal;}
    }
    static final class Page {
        final List<Discovery> discoveries;final boolean more,current;
        Page(List<Discovery> discoveries,boolean more,boolean current){
            this.discoveries=Collections.unmodifiableList(new ArrayList<>(discoveries));this.more=more;this.current=current;
        }
    }
    interface PageLoader {void load(long after,java.util.function.Consumer<Page> complete);}
    /** A bounded recognition-ordered details page. At most 17 compact records retained, no geometry. */
    static Page page(Context context,MilepostDiscoveryLedger ledger,String user,long afterOrdinal,BooleanSupplier cancelled)throws Exception {
        SQLiteDatabase d=ledger.db();d.beginTransaction();
        try {
            boolean current=MilepostAchievementDeriver.state(context,ledger,user).getBoolean("current");
            if(!current)return new Page(Collections.emptyList(),false,false);
            Comparator<Discovery> order=Comparator.comparingLong((Discovery x)->x.ordinal).thenComparing(x->x.marker);
            PriorityQueue<Discovery> selected=new PriorityQueue<>(17,order.reversed());String after="";
            for(;;){int n=0;MilepostArchiveReader.check(cancelled);
                try(Cursor c=MilepostAchievements.factPage(d,user,after,true)){
                    while(c.moveToNext()){
                        MilepostArchiveReader.check(cancelled);after=c.getString(0);n++;
                        long ordinal=c.isNull(8)?0:c.getLong(8);
                        if(ordinal>afterOrdinal){
                            String date=c.isNull(3)?null:Instant.parse(c.getString(3)).atOffset(java.time.ZoneOffset.UTC).toLocalDate().toString();
                            selected.add(new Discovery(after,date,ordinal));if(selected.size()>17)selected.poll();
                        }
                    }
                }
                if(n<16)break;
            }
            List<Discovery> rows=new ArrayList<>(selected);rows.sort(order);boolean more=rows.size()>16;if(more)rows.remove(16);
            current=MilepostAchievementDeriver.state(context,ledger,user).getBoolean("current");
            return new Page(current?rows:Collections.emptyList(),more,current);
        }finally{d.endTransaction();}
    }
    /** Explicit adapter for a future host. It is not registered with any production lifecycle. */
    static PageLoader pages(Context context,String database,String user){
        Context app=context.getApplicationContext();android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
        return (after,complete)->ScreenDataLoader.execute(()->{
            Page result;
            try(MilepostDiscoveryLedger ledger=new MilepostDiscoveryLedger(app,database);
                MilepostReconciliationJob.CaptureGuard guard=new MilepostReconciliationJob.CaptureGuard(app,new java.util.concurrent.atomic.AtomicBoolean(),Long.MAX_VALUE)){
                result=page(app,ledger,user,after,guard);
            }catch(Exception unavailable){result=new Page(Collections.emptyList(),false,false);}
            Page delivered=result;main.post(()->complete.accept(delivered));
        });
    }
    private MilepostPresentationReader(){}
}
