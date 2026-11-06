package com.roadprints.capture;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Looper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Inactive derived evidence store. No caller in app lifecycle, awards or JourneyStore.
 * All entrypoints must run on a worker. Source revisions belong to the caller's durable
 * change feed, not wall-clock time. This ledger never edits source journeys. */
final class MilepostDiscoveryLedger implements AutoCloseable {
    static final int SCHEMA = 2, PAGE = 16, MAX_CATALOGUE = 4096;
    static final String EVALUATOR = MilepostJourneyEvidence.REVISION + "/" + MilepostProximity.Policy.V1.revision;
    enum State { DISCOVERED, ASSESSED_NO_DISCOVERY, PENDING_EVIDENCE, PENDING_CATALOGUE, QUEUED, DELETED }

    /** Reviewed canonical identity; aliases are not additional physical posts. Dated
     * locations are deliberately required before this store can assess a marker. */
    static final class Marker {
        final String id;
        final double lon, lat;
        final boolean eligible;
        final long from, until;
        final List<String> aliases;
        Marker(String id, double lon, double lat, boolean eligible, long from, long until, List<String> aliases) {
            require(id); if (!new MilepostProximity.Point(lon,lat).valid() || from >= until)
                throw new IllegalArgumentException("Invalid dated marker");
            this.id=id; this.lon=lon; this.lat=lat; this.eligible=eligible;
            this.from=from; this.until=until; this.aliases=new ArrayList<>(aliases);
            if (aliases.size()>32) throw new IllegalArgumentException("Too many aliases");
            for (String alias:aliases) require(alias);
        }
    }
    interface EvidenceLoader {
        /** Load one bounded source row through its trusted validator; never an entire archive. */
        MilepostProximity.Evidence load(String user, String journey) throws Exception;
    }
    interface CommitFence {
        /** Validate committed archive identity under its short replacement lock, then commit. */
        void commit(String journey, Runnable transaction);
    }
    static final class ArchiveSource {
        final String id,stamp;
        ArchiveSource(Cursor c){id=c.getString(0);stamp=c.getString(1);}
    }
    String scanCursor(String user,String phase) {
        String value=scalar(db(),"SELECT cursor FROM archive_cursor WHERE user=? AND phase=?",user,phase);
        return value==null?"":value;
    }
    void scanCursor(String user,String phase,String cursor) {
        db().insertWithOnConflict("archive_cursor",null,values("user",user,"phase",phase,"cursor",cursor),SQLiteDatabase.CONFLICT_REPLACE);
    }
    String archiveStamp(String user,String id) {
        return scalar(db(),"SELECT stamp FROM archive_source WHERE user=? AND id=?",user,id);
    }
    List<ArchiveSource> archivePage(String user,String after,int limit) {
        if(limit<1||limit>32)throw new IllegalArgumentException("Archive page limit");
        List<ArchiveSource> out=new ArrayList<>();
        try(Cursor c=db().rawQuery("SELECT id,stamp FROM archive_source WHERE user=? AND id>? ORDER BY id LIMIT "+limit,new String[]{user,after})) {
            while(c.moveToNext())out.add(new ArchiveSource(c));
        }
        return out;
    }
    /** Feed and source revision advance in the same transaction, so crashes cannot
     * acknowledge a change without retracting stale proofs. Versions never use time. */
    void archiveChanged(String user,String id,String stamp,String evidenceToken,boolean deleted) {
        SQLiteDatabase d=db();d.beginTransaction();
        try {
            if(!stamp.equals(archiveStamp(user,id))) {
                long version=number(d,"SELECT version FROM journey WHERE user=? AND id=?",user,id)+1;
                sourceChanged(user,id,version,evidenceToken,deleted);
                d.insertWithOnConflict("archive_source",null,values("user",user,"id",id,"stamp",stamp),SQLiteDatabase.CONFLICT_REPLACE);
            }
            d.setTransactionSuccessful();
        }finally {d.endTransaction();}
    }
    static final class Discovery {
        final String marker, journey, eventDate;
        final long ordinal, firstRecognised;
        Discovery(Cursor c) { marker=c.getString(0); journey=c.getString(1); eventDate=c.getString(2);
            ordinal=c.getLong(3); firstRecognised=c.getLong(4); }
    }
    private final Helper helper;
    MilepostDiscoveryLedger(Context context, String databaseName) {
        worker(); helper=new Helper(context.getApplicationContext(),databaseName);
        SQLiteDatabase d=helper.getWritableDatabase(); // Explicit construction only; no production migration hook.
        d.beginTransaction();
        try {
            String prior=scalar(d,"SELECT evaluator FROM runtime WHERE singleton=1");
            if(!EVALUATOR.equals(prior)) {
                d.execSQL("UPDATE recognition SET invalidation='EVALUATOR_CHANGED'");
                d.delete("assessment",null,null);
                d.execSQL("UPDATE journey SET cursor='',state='QUEUED' WHERE deleted=0");
                d.insertWithOnConflict("runtime",null,values("singleton",1,"evaluator",EVALUATOR),SQLiteDatabase.CONFLICT_REPLACE);
            }
            d.setTransactionSuccessful();
        } finally {d.endTransaction();}
    }
    private static void worker() {
        if (Looper.myLooper()==Looper.getMainLooper()) throw new IllegalStateException("Milepost ledger requires a worker");
    }
    private static void require(String s) {
        if (!MilepostProximity.text(s) || s.length()>512) throw new IllegalArgumentException("Missing/oversized identity");
    }
    private SQLiteDatabase db() { worker(); return helper.getWritableDatabase(); }
    private static ContentValues values(Object... pairs) {
        ContentValues v=new ContentValues();
        for(int i=0;i<pairs.length;i+=2) {
            String k=(String)pairs[i]; Object x=pairs[i+1];
            if(x==null)v.putNull(k); else if(x instanceof Long)v.put(k,(Long)x);
            else if(x instanceof Integer)v.put(k,(Integer)x); else if(x instanceof Double)v.put(k,(Double)x);
            else v.put(k,x.toString());
        }
        return v;
    }
    private static long number(SQLiteDatabase d,String sql,String... args) {
        try(Cursor c=d.rawQuery(sql,args)){return c.moveToFirst()?c.getLong(0):0;}
    }
    private static String scalar(SQLiteDatabase d,String sql,String... args) {
        try(Cursor c=d.rawQuery(sql,args)){return c.moveToFirst()?c.getString(0):null;}
    }
    /** Complete, hash-pinned, externally verified catalogue. No downloader or candidate
     * promotion here. Caller must verify bytes/schema/identity before supplying rows. */
    void install(int revision, String sha256, List<Marker> markers) {
        SQLiteDatabase d=db();
        if(revision<1 || sha256==null || !sha256.matches("[0-9a-f]{64}") || markers.size()>MAX_CATALOGUE)
            throw new IllegalArgumentException("Invalid catalogue envelope");
        d.beginTransaction();
        try {
            long old=number(d,"SELECT revision FROM catalogue WHERE singleton=1");
            String oldHash=scalar(d,"SELECT sha FROM catalogue WHERE singleton=1");
            if(revision<old || (revision==old&&!sha256.equals(oldHash)))throw new IllegalArgumentException("Catalogue rollback/conflict");
            // Validate the supplied rows even on an idempotent install. Hash is immutable,
            // but never trust a caller to change geometry under the same pin.
            String fingerprint=markerFingerprint(markers);
            if(revision==old) {
                if(!fingerprint.equals(scalar(d,"SELECT fingerprint FROM catalogue WHERE singleton=1")))
                    throw new IllegalArgumentException("Catalogue content changed under pin");
            } else {
                for(Marker m:markers) {
                    if(number(d,"SELECT COUNT(*) FROM identity_registry WHERE id=? AND retired=1",m.id)>0)
                        throw new IllegalArgumentException("Retired physical identity requires reviewed migration");
                    d.insertWithOnConflict("identity_registry",null,values("id",m.id,"retired",0),SQLiteDatabase.CONFLICT_IGNORE);
                }
                d.delete("marker",null,null);
                for(Marker m:markers) {
                    d.insertOrThrow("marker",null,values("id",m.id,"lon",m.lon,"lat",m.lat,"eligible",m.eligible?1:0,"valid_from",m.from,"valid_until",m.until));
                    for(String alias:m.aliases) {
                        String owner=scalar(d,"SELECT marker FROM alias WHERE alias=?",alias);
                        if(owner!=null&&!m.id.equals(owner))throw new IllegalArgumentException("Alias cannot change physical identity");
                        d.insertWithOnConflict("alias",null,values("alias",alias,"marker",m.id),SQLiteDatabase.CONFLICT_IGNORE);
                    }
                }
                d.execSQL("UPDATE identity_registry SET retired=1 WHERE id NOT IN (SELECT id FROM marker)");
                d.execSQL("UPDATE recognition SET invalidation='CATALOGUE_CHANGED'");
                d.insertWithOnConflict("catalogue",null,values("singleton",1,"revision",revision,"sha",sha256,"fingerprint",fingerprint),SQLiteDatabase.CONFLICT_REPLACE);
                // Invalidate all prior facts immediately, before any bounded rescan.
                d.execSQL("UPDATE journey SET cursor='', state='QUEUED' WHERE deleted=0");
                d.delete("assessment",null,null);
            }
            d.setTransactionSuccessful();
        } finally {d.endTransaction();}
    }
    private static String markerFingerprint(List<Marker> markers) {
        try {
            org.json.JSONArray rows=new org.json.JSONArray();
            java.util.List<Marker> sorted=new ArrayList<>(markers); sorted.sort((a,b)->a.id.compareTo(b.id));
            for(Marker m:sorted) {
                List<String> aliases=new ArrayList<>(m.aliases); java.util.Collections.sort(aliases);
                rows.put(new org.json.JSONArray().put(m.id).put(m.lon).put(m.lat).put(m.eligible)
                        .put(m.from).put(m.until).put(new org.json.JSONArray(aliases)));
            }
            return MilepostJourneyEvidence.digestJson(new org.json.JSONObject().put("markers",rows));
        } catch(Exception e) {throw new IllegalArgumentException("Catalogue fingerprint",e);}
    }
    /** Correction/deletion must be registered before reassessment. Tombstones prevent
     * delayed jobs resurrecting deleted journeys. Higher versions may explicitly re-add. */
    void sourceChanged(String user,String journey,long version,String token,boolean deleted) {
        require(user); require(journey); require(token); if(version<1)throw new IllegalArgumentException("Invalid source version");
        SQLiteDatabase d=db(); d.beginTransaction();
        try {
            try(Cursor c=d.rawQuery("SELECT version,token,deleted FROM journey WHERE user=? AND id=?",new String[]{user,journey})) {
                if(c.moveToFirst()) {
                    if(version<c.getLong(0))throw new ConcurrentModificationException("Stale source revision");
                    if(version==c.getLong(0)) {
                        if(!token.equals(c.getString(1)) || deleted!=(c.getInt(2)==1))throw new ConcurrentModificationException("Conflicting source revision");
                        d.setTransactionSuccessful();return;
                    }
                }
            }
            d.execSQL("UPDATE recognition SET invalidation='SOURCE_CHANGED_OR_DELETED' WHERE user=? AND marker IN (SELECT marker FROM assessment WHERE user=? AND journey=?)",new Object[]{user,user,journey});
            d.delete("assessment","user=? AND journey=?",new String[]{user,journey});
            d.insertWithOnConflict("journey",null,values("user",user,"id",journey,"version",version,"token",token,
                    "deleted",deleted?1:0,"cursor","","state",deleted?State.DELETED.name():State.QUEUED.name()),SQLiteDatabase.CONFLICT_REPLACE);
            d.setTransactionSuccessful();
        } finally {d.endTransaction();}
    }
    /** Requeue unresolved/negative/positive work after validator upgrades or an explicit
     * retry. Pending is never treated as a completed negative cache entry. */
    void reassess(String user,String journey) {
        SQLiteDatabase d=db(); d.beginTransaction();
        try {
            d.execSQL("UPDATE recognition SET invalidation='SOURCE_CHANGED_OR_DELETED' WHERE user=? AND marker IN (SELECT marker FROM assessment WHERE user=? AND journey=?)",new Object[]{user,user,journey});
            d.delete("assessment","user=? AND journey=?",new String[]{user,journey});
            d.execSQL("UPDATE journey SET cursor='',state='QUEUED' WHERE user=? AND id=? AND deleted=0",new Object[]{user,journey});
            d.setTransactionSuccessful();
        } finally {d.endTransaction();}
    }
    /** One call loads one source and assesses at most PAGE markers. Cursor and facts
     * commit atomically. A crash/cancel/stale source rolls back this page; retries are safe.
     * Returns false when no queued work remains, not when all history was assessable. */
    boolean processNext(String user,EvidenceLoader loader,BooleanSupplier cancelled,long recognisedAt) throws Exception {
        return processNext(user,loader,cancelled,recognisedAt,false,(id,transaction)->transaction.run());
    }
    boolean processNext(String user,EvidenceLoader loader,BooleanSupplier cancelled,long recognisedAt,
                        boolean spatial,CommitFence fence) throws Exception {
        require(user); if(recognisedAt<0)throw new IllegalArgumentException("Invalid recognition time");
        SQLiteDatabase d=db(); String journey,token,after; long version; int revision;
        try(Cursor c=d.rawQuery("SELECT id,token,version,cursor FROM journey WHERE user=? AND state='QUEUED' AND deleted=0 ORDER BY id LIMIT 1",new String[]{user})) {
            if(!c.moveToFirst())return false;
            journey=c.getString(0);token=c.getString(1);version=c.getLong(2);after=c.getString(3);
        }
        revision=(int)number(d,"SELECT revision FROM catalogue WHERE singleton=1");
        MilepostProximity.Evidence e=loader.load(user,journey);
        if(e==null || !journey.equals(e.journeyId()) || !token.equals(e.token()))throw new ConcurrentModificationException("Source evidence changed");
        for(String field:new String[]{e.source(),e.validationRevision(),e.mode()})
            if(field!=null&&field.length()>512)throw new IllegalArgumentException("Oversized evidence metadata");
        MilepostArchiveReader.check(cancelled);
        MilepostCandidateBounds bounds=spatial?MilepostCandidateBounds.of(bounded(e),cancelled):null;
        String selection=bounds==null?"1=1":bounds.sql();
        String markerTable=spatial?"marker INDEXED BY marker_bounds":"marker";
        List<Marker> page=new ArrayList<>();
        try(Cursor c=d.rawQuery("SELECT id,lon,lat,eligible,valid_from,valid_until FROM "+markerTable+" WHERE id>? AND ("+selection+") ORDER BY id LIMIT "+PAGE,new String[]{after})) {
            while(c.moveToNext())page.add(new Marker(c.getString(0),c.getDouble(1),c.getDouble(2),c.getInt(3)==1,c.getLong(4),c.getLong(5),java.util.Collections.emptyList()));
        }
        List<MilepostProximity.Result> results=new ArrayList<>();
        long event=0,end=0; boolean dated=true;
        try {event=Instant.parse(e.journeyDate()).toEpochMilli();end=Instant.parse(e.journeyEndDate()).toEpochMilli();
            if(end<=event)dated=false;
        }catch(Exception invalid){dated=false;}
        for(Marker m:page) {
            if(cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())throw new CancellationException();
            boolean validDate=dated&&event>=m.from&&end<=m.until;
            MilepostProximity.Marker target=new MilepostProximity.Marker(m.id,new MilepostProximity.Point(m.lon,m.lat),m.eligible&&validDate,Integer.toString(revision));
            results.add(MilepostProximity.assess(target,bounded(e),MilepostProximity.Policy.V1,cancelled));
        }
        final boolean sourceDated=dated;
        final long sourceEvent=event,sourceEnd=end;
        fence.commit(journey,()->{
        d.beginTransaction();
        try {
            // Fence both simultaneous workers and source/catalogue changes during evaluation.
            if(number(d,"SELECT COUNT(*) FROM journey WHERE user=? AND id=? AND version=? AND token=? AND cursor=? AND state='QUEUED' AND deleted=0",user,journey,Long.toString(version),token,after)!=1
                    || revision!=number(d,"SELECT revision FROM catalogue WHERE singleton=1"))throw new ConcurrentModificationException("Ledger inputs changed");
            for(int i=0;i<page.size();i++) {
                MilepostProximity.Result r=results.get(i); Marker m=page.get(i);
                if(r.outcome==MilepostProximity.Outcome.CANCELLED || cancelled.getAsBoolean())throw new CancellationException();
                State state=r.outcome==MilepostProximity.Outcome.QUALIFIED?State.DISCOVERED:
                        r.outcome==MilepostProximity.Outcome.OUTSIDE_RADIUS?State.ASSESSED_NO_DISCOVERY:State.PENDING_EVIDENCE;
                if(!m.eligible || !sourceDated || sourceEvent<m.from || sourceEnd>m.until)state=State.PENDING_CATALOGUE;
                d.insertWithOnConflict("assessment",null,values("user",user,"journey",journey,"marker",m.id,"state",state.name(),
                        "reason",r.outcome.name(),"token",token,"source_version",version,"catalogue",revision,"evaluator",EVALUATOR,
                        "validator",e.validationRevision(),"source",e.source(),"mode",e.mode(),"event_date",sourceDated?e.journeyDate():null,
                        "event_ms",sourceDated?sourceEvent:null,"precision","journey","recognised",recognisedAt,
                        "distance",Double.isFinite(r.closestMetres)?r.closestMetres:null,"nearest_lon",r.closestPoint==null?null:r.closestPoint.lon,
                        "nearest_lat",r.closestPoint==null?null:r.closestPoint.lat,"post_lon",m.lon,"post_lat",m.lat,
                        "section",r.section,"source_start",r.sourceStart,"source_end",r.sourceEnd,"method",r.method),SQLiteDatabase.CONFLICT_REPLACE);
                if(state==State.DISCOVERED) {
                    // Recognition order never changes when earlier history is backfilled.
                    long ordinal=number(d,"SELECT COALESCE(MAX(ordinal),0)+1 FROM recognition WHERE user=?",user);
                    d.insertWithOnConflict("recognition",null,values("user",user,"marker",m.id,"ordinal",ordinal,"first_recognised",recognisedAt,"first_journey",journey,"first_token",token,"first_event",e.journeyDate(),"first_catalogue",revision,"invalidation",null),SQLiteDatabase.CONFLICT_IGNORE);
                }
            }
            String cursor=page.isEmpty()?after:page.get(page.size()-1).id;
            boolean more=number(d,"SELECT COUNT(*) FROM "+markerTable+" WHERE id>? AND ("+selection+")",cursor)>0;
            String state=State.QUEUED.name();
            if(!more) {
                if(spatial&&(e.problem()!=null||(!bounds.any&&!bounds.global)))state=State.PENDING_EVIDENCE.name();
                else if(number(d,"SELECT COUNT(*) FROM assessment WHERE user=? AND journey=? AND state='DISCOVERED'",user,journey)>0)state=State.DISCOVERED.name();
                else if(number(d,"SELECT COUNT(*) FROM assessment WHERE user=? AND journey=? AND state IN ('PENDING_EVIDENCE','PENDING_CATALOGUE')",user,journey)>0)
                    state=State.PENDING_EVIDENCE.name();
                else state=number(d,"SELECT COUNT(*) FROM marker WHERE eligible=1")>0?State.ASSESSED_NO_DISCOVERY.name():State.PENDING_CATALOGUE.name();
            }
            if(cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())throw new CancellationException();
            d.update("journey",values("cursor",cursor,"state",state),"user=? AND id=?",new String[]{user,journey});
            d.setTransactionSuccessful();
        } finally {d.endTransaction();}
        });
        return true;
    }
    private static MilepostProximity.Evidence bounded(MilepostProximity.Evidence e) {
        return new MilepostProximity.Evidence() {
            public String journeyId(){return e.journeyId();} public String journeyDate(){return e.journeyDate();}
            public String journeyEndDate(){return e.journeyEndDate();} public String mode(){return e.mode();}
            public String source(){return e.source();} public String token(){return e.token();}
            public String validationRevision(){return e.validationRevision();} public MilepostProximity.Outcome problem(){return e.problem();}
            public Iterable<MilepostProximity.Segment> segments(){return ()->new java.util.Iterator<MilepostProximity.Segment>() {
                final java.util.Iterator<MilepostProximity.Segment> delegate=e.segments().iterator(); int count;
                public boolean hasNext(){return delegate.hasNext();}
                public MilepostProximity.Segment next(){if(++count>MilepostJourneyEvidence.MAX_POINTS)throw new IllegalArgumentException("Unbounded evidence");return delegate.next();}
            };}
        };
    }
    State assessmentState(String user,String journey,String marker) {
        String s=scalar(db(),"SELECT state FROM assessment WHERE user=? AND journey=? AND marker=?",user,journey,marker);
        return s==null?null:State.valueOf(s);
    }
    State state(String user,String journey) {
        String s=scalar(db(),"SELECT state FROM journey WHERE user=? AND id=?",user,journey);
        return s==null?null:State.valueOf(s);
    }
    /** Keyset paging: no all-discoveries/all-archive materialisation. One current fact per
     * physical identity even when many overlapping/reimported journeys support it. */
    List<Discovery> discoveries(String user,String after,int limit) {
        if(limit<1 || limit>PAGE)throw new IllegalArgumentException("Invalid page size");
        List<Discovery> out=new ArrayList<>();
        try(Cursor c=db().rawQuery("SELECT a.marker,a.journey,a.event_date,r.ordinal,r.first_recognised FROM assessment a JOIN recognition r ON r.user=a.user AND r.marker=a.marker WHERE a.user=? AND a.marker>? AND a.state='DISCOVERED' AND NOT EXISTS (SELECT 1 FROM journey j WHERE j.user=a.user AND j.id=a.journey AND j.state='QUEUED') AND NOT EXISTS (SELECT 1 FROM assessment b WHERE b.user=a.user AND b.marker=a.marker AND b.state='DISCOVERED' AND NOT EXISTS (SELECT 1 FROM journey j WHERE j.user=b.user AND j.id=b.journey AND j.state='QUEUED') AND (b.event_ms<a.event_ms OR (b.event_ms=a.event_ms AND b.journey<a.journey))) ORDER BY a.marker LIMIT "+limit,new String[]{user,after})) {
            while(c.moveToNext())out.add(new Discovery(c));
        }
        return out;
    }
    /** Durable first-recognition history remains after withdrawal; no entitlement state. */
    boolean previouslyRecognised(String user,String marker) {
        return number(db(),"SELECT COUNT(*) FROM recognition WHERE user=? AND marker=?",user,marker)>0;
    }
    String withdrawalReason(String user,String marker) {
        return scalar(db(),"SELECT invalidation FROM recognition WHERE user=? AND marker=? AND NOT EXISTS (SELECT 1 FROM assessment WHERE user=? AND marker=? AND state='DISCOVERED')",user,marker,user,marker);
    }
    String canonical(String alias) {return scalar(db(),"SELECT marker FROM alias WHERE alias=?",alias);}
    long proofCount(String user) {return number(db(),"SELECT COUNT(*) FROM assessment WHERE user=? AND state='DISCOVERED'",user);}
    @Override public void close(){worker();helper.close();}

    private static final class Helper extends SQLiteOpenHelper {
        Helper(Context context,String name){super(context,name,null,SCHEMA);}
        @Override public void onConfigure(SQLiteDatabase d){d.setForeignKeyConstraintsEnabled(true);}
        @Override public void onCreate(SQLiteDatabase d) {
            d.execSQL("CREATE TABLE runtime(singleton INTEGER PRIMARY KEY,evaluator TEXT NOT NULL)");
            d.execSQL("CREATE TABLE catalogue(singleton INTEGER PRIMARY KEY CHECK(singleton=1),revision INTEGER NOT NULL,sha TEXT NOT NULL,fingerprint TEXT NOT NULL)");
            d.execSQL("CREATE TABLE marker(id TEXT PRIMARY KEY,lon REAL NOT NULL,lat REAL NOT NULL,eligible INTEGER NOT NULL,valid_from INTEGER NOT NULL,valid_until INTEGER NOT NULL)");
            archiveTables(d);
            d.execSQL("CREATE TABLE identity_registry(id TEXT PRIMARY KEY,retired INTEGER NOT NULL)");
            d.execSQL("CREATE TABLE alias(alias TEXT PRIMARY KEY,marker TEXT NOT NULL REFERENCES identity_registry(id))");
            d.execSQL("CREATE TABLE journey(user TEXT NOT NULL,id TEXT NOT NULL,version INTEGER NOT NULL,token TEXT NOT NULL,deleted INTEGER NOT NULL,cursor TEXT NOT NULL,state TEXT NOT NULL,PRIMARY KEY(user,id))");
            d.execSQL("CREATE INDEX journey_queue ON journey(user,state,id)");
            d.execSQL("CREATE TABLE assessment(user TEXT NOT NULL,journey TEXT NOT NULL,marker TEXT NOT NULL,state TEXT NOT NULL,reason TEXT NOT NULL,token TEXT NOT NULL,source_version INTEGER NOT NULL,catalogue INTEGER NOT NULL,evaluator TEXT NOT NULL,validator TEXT,source TEXT,mode TEXT,event_date TEXT,event_ms INTEGER,precision TEXT NOT NULL,recognised INTEGER NOT NULL,distance REAL,nearest_lon REAL,nearest_lat REAL,post_lon REAL,post_lat REAL,section INTEGER,source_start INTEGER,source_end INTEGER,method TEXT,PRIMARY KEY(user,journey,marker),FOREIGN KEY(user,journey) REFERENCES journey(user,id))");
            d.execSQL("CREATE INDEX assessment_discovery ON assessment(user,marker,state,event_ms,journey)");
            d.execSQL("CREATE TABLE recognition(user TEXT NOT NULL,marker TEXT NOT NULL,ordinal INTEGER NOT NULL,first_recognised INTEGER NOT NULL,first_journey TEXT NOT NULL,first_token TEXT NOT NULL,first_event TEXT NOT NULL,first_catalogue INTEGER NOT NULL,invalidation TEXT,PRIMARY KEY(user,marker),UNIQUE(user,ordinal))");
        }
        private static void archiveTables(SQLiteDatabase d) {
            d.execSQL("CREATE INDEX IF NOT EXISTS marker_bounds ON marker(lat,lon,id)");
            d.execSQL("CREATE TABLE archive_source(user TEXT NOT NULL,id TEXT NOT NULL,stamp TEXT NOT NULL,PRIMARY KEY(user,id))");
            d.execSQL("CREATE TABLE archive_cursor(user TEXT NOT NULL,phase TEXT NOT NULL,cursor TEXT NOT NULL,PRIMARY KEY(user,phase))");
        }
        @Override public void onUpgrade(SQLiteDatabase d,int oldVersion,int newVersion) {
            if(oldVersion==1&&newVersion==2){archiveTables(d);return;}
            throw new IllegalStateException("Unsupported milepost schema; preserve database");
        }
        @Override public void onDowngrade(SQLiteDatabase d,int oldVersion,int newVersion) {throw new IllegalStateException("Future milepost schema; preserve database");}
    }
}
