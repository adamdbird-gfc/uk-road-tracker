package com.roadprints.capture;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Inactive, compact derivation. No AchievementStore definitions/preferences/celebrations.
 * Reads only ledger facts; the explicit coordinator supplies archive and NCN fences. */
final class MilepostAchievements {
    static final String[] IDS={"millennium-jon-mills","millennium-andrew-rowe","millennium-iain-mccoll",
            "millennium-david-dudgeon","millennium-john-grimshaw","millennium-charlie-harrow","millennium-in-honour-of-time"};
    private static final List<String> DESIGNERS=Arrays.asList("mills","rowe","mccoll","dudgeon");
    static final class Fact {
        final String marker,journey,token,date,designer;
        final long event;
        final int section;
        final boolean verified;
        Fact(Cursor c) {
            marker=c.getString(0);journey=c.getString(1);token=c.getString(2);date=c.getString(3);
            event=c.getLong(4);section=c.getInt(5);designer=c.getString(6);verified=c.getInt(7)==1;
        }
        JSONObject reference()throws Exception{return new JSONObject().put("marker",marker).put("journey",journey)
                .put("token",token).put("section",section).put("event_date",date).put("precision","journey");}
    }
    private static final Comparator<Fact> ORDER=Comparator.comparingLong((Fact f)->f.event)
            .thenComparing(f->f.journey).thenComparingInt(f->f.section).thenComparing(f->f.marker);
    static final class Award {
        boolean earned;
        String date,precision="unknown";
        JSONArray support=new JSONArray();
        void from(Fact f)throws Exception{earned=true;date=f.date;precision="journey";support.put(f.reference());}
    }
    static final class Prepared {
        final long generation;
        final String catalogue;
        final Award[] awards=new Award[7];
        int count;
        Prepared(long generation,String catalogue){this.generation=generation;this.catalogue=catalogue;for(int i=0;i<7;i++)awards[i]=new Award();}
    }
    static long generation(SQLiteDatabase d){return number(d,"SELECT generation FROM derivation_input WHERE singleton=1");}
    static String catalogue(SQLiteDatabase d){return scalar(d,"SELECT revision||':'||sha||':'||fingerprint FROM catalogue WHERE singleton=1");}
    static Prepared prepare(MilepostDiscoveryLedger ledger,String user,JSONObject ncn,BooleanSupplier cancelled)throws Exception {
        SQLiteDatabase d=ledger.db();long generation=generation(d);String pin=catalogue(d);
        if(pin==null||!MilepostDiscoveryLedger.EVALUATOR.equals(scalar(d,"SELECT evaluator FROM runtime WHERE singleton=1"))
                ||number(d,"SELECT COUNT(*) FROM journey WHERE user=? AND state='QUEUED'",user)>0)
            throw new ConcurrentModificationException("Incomplete ledger");
        Prepared result=new Prepared(generation,pin);
        Fact[] designers=new Fact[4];PriorityQueue<Fact> seven=new PriorityQueue<>(7,ORDER.reversed());
        // Canonical physical identity, one earliest supported fact per marker. The query
        // keeps alternate proofs in SQLite and never loads source geometry or all proofs.
        String valid=" FROM assessment a JOIN journey j ON j.user=a.user AND j.id=a.journey JOIN marker m ON m.id=a.marker"
                +" WHERE a.user=? AND a.state='DISCOVERED' AND j.state!='QUEUED' AND j.deleted=0"
                +" AND a.token=j.token AND a.source_version=j.version AND a.catalogue=(SELECT revision FROM catalogue WHERE singleton=1)"
                +" AND a.evaluator=? AND m.eligible=1 AND a.reason='QUALIFIED' AND a.event_ms IS NOT NULL"
                +" AND a.source IS NOT NULL AND a.validator IS NOT NULL AND a.mode IN ('walking','running','cycling')";
        String after="";
        for(;;) {
            MilepostArchiveReader.check(cancelled);int read=0;
            try(Cursor c=d.rawQuery("SELECT a.marker,a.journey,a.token,a.event_date,a.event_ms,a.section,m.designer,m.designer_verified"+valid
                    +" AND a.marker>? AND NOT EXISTS (SELECT 1 FROM assessment b JOIN journey k ON k.user=b.user AND k.id=b.journey"
                    +" WHERE b.user=a.user AND b.marker=a.marker AND b.state='DISCOVERED' AND k.state!='QUEUED' AND k.deleted=0"
                    +" AND b.token=k.token AND b.source_version=k.version AND b.catalogue=a.catalogue AND b.evaluator=a.evaluator"
                    +" AND b.reason='QUALIFIED' AND b.event_ms IS NOT NULL AND b.source IS NOT NULL AND b.validator IS NOT NULL"
                    +" AND b.mode IN ('walking','running','cycling') AND (b.event_ms<a.event_ms OR (b.event_ms=a.event_ms AND b.journey<a.journey)))"
                    +" ORDER BY a.marker LIMIT 16",new String[]{user,MilepostDiscoveryLedger.EVALUATOR,after})) {
                while(c.moveToNext()) {
                    MilepostArchiveReader.check(cancelled);Fact f=new Fact(c);after=f.marker;read++;result.count++;
                    int designer=DESIGNERS.indexOf(f.designer);
                    if(f.verified&&designer>=0&&(designers[designer]==null||ORDER.compare(f,designers[designer])<0))designers[designer]=f;
                    seven.add(f);if(seven.size()>7)seven.poll();
                }
            }
            if(read<16)break;
        }
        for(int i=0;i<4;i++)if(designers[i]!=null)result.awards[i].from(designers[i]);
        if(ncn!=null){result.awards[4].earned=true;result.awards[4].support.put(ncn);}
        if(result.count>=7) {
            List<Fact> first=new ArrayList<>(seven);first.sort(ORDER);Award harrow=result.awards[5];
            harrow.earned=true;harrow.date=first.get(6).date;harrow.precision="journey";
            for(Fact f:first)harrow.support.put(f.reference());
        }
        Award parent=result.awards[6];parent.earned=true;String latest=null;boolean known=true;
        for(int i=0;i<6;i++) {
            Award a=result.awards[i];parent.earned&=a.earned;
            if(a.date==null)known=false;else if(latest==null||java.time.Instant.parse(a.date).isAfter(java.time.Instant.parse(latest)))latest=a.date;
            parent.support.put(IDS[i]);
        }
        if(parent.earned&&known){parent.date=latest;parent.precision="journey";}
        if(!parent.earned)parent.support=new JSONArray();
        MilepostArchiveReader.check(cancelled);
        if(generation!=generation(d))throw new ConcurrentModificationException("Ledger changed during derivation");
        return result;
    }
    /** Caller holds SQLite writer + NCN + archive fences, including endTransaction. */
    static void publish(SQLiteDatabase d,String user,Prepared prepared,long archive,String ncnToken,long now,BooleanSupplier cancelled)throws Exception {
        if(prepared.generation!=generation(d)||!prepared.catalogue.equals(catalogue(d)))throw new ConcurrentModificationException("Ledger changed");
        for(int i=0;i<7;i++) {
            MilepostArchiveReader.check(cancelled);Award a=prepared.awards[i];String id=IDS[i];
            d.execSQL("INSERT OR IGNORE INTO achievement_state(user,id,earned,precision,support) VALUES(?,?,0,'unknown','[]')",new Object[]{user,id});
            if(a.earned) {
                long ordinal=number(d,"SELECT COALESCE(MAX(ordinal),0)+1 FROM achievement_state WHERE user=?",user);
                d.execSQL("UPDATE achievement_state SET first_recognised=?,ordinal=?,first_event=?,first_precision=? WHERE user=? AND id=? AND first_recognised IS NULL",
                        new Object[]{now,ordinal,a.date,a.precision,user,id});
            }
            d.execSQL("UPDATE achievement_state SET earned=?,event_date=?,precision=?,support=? WHERE user=? AND id=?",
                    new Object[]{a.earned?1:0,a.date,a.precision,a.support.toString(),user,id});
        }
        d.execSQL("INSERT OR REPLACE INTO achievement_snapshot VALUES(?,?,?,?,?,?,?,?)",
                new Object[]{user,archive,prepared.generation,prepared.catalogue,MilepostDiscoveryLedger.EVALUATOR,ncnToken,prepared.count,now});
        MilepostArchiveReader.check(cancelled);
    }
    /** Diagnostics only; previous complete result remains visible with explicit freshness. */
    static JSONObject state(MilepostDiscoveryLedger ledger,String user)throws Exception {
        SQLiteDatabase d=ledger.db();JSONObject out=new JSONObject();JSONArray rows=new JSONArray();
        try(Cursor c=d.rawQuery("SELECT id,earned,first_recognised,ordinal,first_event,first_precision,event_date,precision,support FROM achievement_state WHERE user=? ORDER BY COALESCE(ordinal,999),id",new String[]{user})) {
            while(c.moveToNext())rows.put(new JSONObject().put("id",c.getString(0)).put("earned",c.getInt(1)==1)
                    .put("first_recognised",c.isNull(2)?JSONObject.NULL:c.getLong(2)).put("ordinal",c.isNull(3)?JSONObject.NULL:c.getLong(3))
                    .put("first_event",c.isNull(4)?JSONObject.NULL:c.getString(4)).put("first_precision",c.isNull(5)?JSONObject.NULL:c.getString(5))
                    .put("event_date",c.isNull(6)?JSONObject.NULL:c.getString(6)).put("precision",c.getString(7)).put("support",new JSONArray(c.getString(8))));
        }
        return out.put("rows",rows);
    }
    static long number(SQLiteDatabase d,String sql,String... args){String s=scalar(d,sql,args);return s==null?0:Long.parseLong(s);}
    static String scalar(SQLiteDatabase d,String sql,String... args){try(Cursor c=d.rawQuery(sql,args)){return c.moveToFirst()?c.getString(0):null;}}
}
