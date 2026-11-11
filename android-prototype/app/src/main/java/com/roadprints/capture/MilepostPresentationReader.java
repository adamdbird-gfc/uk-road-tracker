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
            int newPosts=0;long postOrdinal=0,earliest=Long.MAX_VALUE,latest=Long.MIN_VALUE;String from=null,to=null,after="";
            if(current)for(;;){
                MilepostArchiveReader.check(cancelled);int read=0;
                try(Cursor c=MilepostAchievements.factPage(d,user,after,true)){
                    while(c.moveToNext()){
                        MilepostArchiveReader.check(cancelled);
                        after=c.getString(0);read++;
                        long ordinal=c.isNull(8)?0:c.getLong(8);
                        postOrdinal=Math.max(postOrdinal,ordinal);
                        if(ordinal>acknowledgedPosts){
                            newPosts++;
                            // Display calendar dates only. Journey starts do not establish exact passage times.
                            if(!c.isNull(3)){
                                long event=c.getLong(4);
                                if(event<earliest){earliest=event;from=c.getString(3);}
                                if(event>latest){latest=event;to=c.getString(3);}
                            }
                        }
                    }
                }
                if(read<16)break;
            }
            if(from!=null)from=calendarDate(from);if(to!=null)to=calendarDate(to);
            String receipt=MilepostAchievements.scalar(d,"SELECT catalogue||':'||ledger_generation||':'||ncn_token FROM achievement_snapshot WHERE user=?",user);
            // Publication/capture may have changed while compact pages were read. Never announce a stale batch.
            current&=MilepostAchievementDeriver.state(context,ledger,user).getBoolean("current");
            return new MilepostPresentation.Snapshot(current,count,current?newPosts:0,pending,incomplete,postOrdinal,awardOrdinal,
                receipt==null?"":receipt,from,to,awards);
        }finally{d.endTransaction();}
    }
    static Map<String,Discovery> mapDiscoveries(Context context)throws Exception {
        MilepostIntegration gate=MilepostIntegration.PRODUCTION;
        Map<String,Discovery> found=new HashMap<>();
        try(MilepostDiscoveryLedger ledger=new MilepostDiscoveryLedger(context,gate.database)){
            if(!gate.verified(ledger))return found;
            SQLiteDatabase db=ledger.db();
            db.beginTransaction();
            try {
                Map<String,Integer> support=new HashMap<>();
                JSONObject snapshot=MilepostAchievementDeriver.state(context,ledger,gate.user);
                if(snapshot.getBoolean("current")){
                    JSONArray awards=snapshot.getJSONArray("rows");
                    for(int i=0;i<awards.length();i++){
                        JSONObject award=awards.getJSONObject(i);
                        int index=Arrays.asList(MilepostAchievements.IDS).indexOf(award.getString("id"));
                        // Route completion and the collective award do not name a physical Milepost.
                        if(!award.getBoolean("earned")||index<0||index==4||index==6)continue;
                        JSONArray evidence=award.getJSONArray("support");
                        for(int j=0;j<evidence.length();j++){
                            JSONObject fact=evidence.optJSONObject(j);
                            if(fact!=null&&fact.has("marker")){
                                String marker=fact.getString("marker");
                                support.merge(marker,1<<index,(oldValue,newValue)->oldValue|newValue);
                            }
                        }
                    }
                }
                String after="";
                for(;;){
                    int count=0;
                    try(Cursor cursor=MilepostAchievements.factPage(db,gate.user,after)){
                        while(cursor.moveToNext()){
                            after=cursor.getString(0);count++;
                            found.put(after,new Discovery(after,cursor.isNull(3)?null:calendarDate(cursor.getString(3)),
                                    0,4,support.getOrDefault(after,0)));
                        }
                    }
                    if(count<16)return found;
                }
            }finally{db.endTransaction();}
        }
    }
    static final class Discovery {
        private static final String[] DESIGNS={"Fossil Tree","Nautical Fingerpost","Cockerel","Tracks","Millennium Milepost"};
        final String marker,date,designLabel;
        final long ordinal;
        final int design, relatedAwards;
        Discovery(String marker,String date,long ordinal){this(marker,date,ordinal,4);}
        Discovery(String marker,String date,long ordinal,int design){this(marker,date,ordinal,design,0);}
        Discovery(String marker,String date,long ordinal,int design,int relatedAwards){
            this.marker=marker;this.date=date;this.ordinal=ordinal;this.design=design;this.relatedAwards=relatedAwards;
            this.designLabel=DESIGNS[design];
        }
        static int designIndex(String designer){
            if(designer==null)return 4;
            int index=Arrays.asList("mills","rowe","mccoll","dudgeon").indexOf(designer);
            return index<0?4:index;
        }
        static String designLabel(int design){return DESIGNS[design];}
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
                        if(ordinal>afterOrdinal&&(selected.size()<17||ordinal<selected.peek().ordinal)){
                            selected.add(new Discovery(after,c.isNull(3)?null:c.getString(3),ordinal));if(selected.size()>17)selected.poll();
                        }
                    }
                }
                if(n<16)break;
            }
            List<Discovery> rows=new ArrayList<>(selected);rows.sort(order);boolean more=rows.size()>16;if(more)rows.remove(16);
            for(int i=0;i<rows.size();i++){
                Discovery row=rows.get(i);int design=4;
                try(Cursor marker=d.rawQuery("SELECT designer,designer_verified FROM marker WHERE id=?",new String[]{row.marker})){
                    if(marker.moveToFirst()&&marker.getInt(1)==1){design=Discovery.designIndex(marker.getString(0));}
                }
                rows.set(i,new Discovery(row.marker,row.date==null?null:calendarDate(row.date),row.ordinal,design));
            }
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
    private static String calendarDate(String value){return Instant.parse(value).atOffset(java.time.ZoneOffset.UTC).toLocalDate().toString();}
    private MilepostPresentationReader(){}
}
