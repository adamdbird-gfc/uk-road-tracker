package com.roadprints.capture;

import java.util.*;

/** Immutable presentation contract. Eligibility belongs exclusively to Stage 5. */
final class MilepostPresentation {
    static final int ARTWORK_VERSION=2, CIPHER_VERSION=1;
    private static final String TARGET="CHARLIEHARROW";
    private static final int[][] POSITIONS={{},{0,7},{1,8},{2,9},{3,10},{4,11},{5,12},{6}};
    static final String[] THEMES={"Hidden","Seasons","Continents","Ages of Man","Zodiac","Centuries","Final Mystery","Revealed"};
    private static final String[] NAMES={"Jon Mills","Andrew Rowe","Iain McColl","David Dudgeon","John Grimshaw","Charlie Harrow","In Honour of Time"};
    // Conservative draft copy already recorded in verification-register.md. Content review remains a release gate.
    private static final String[] BIOS={
        "Jon Mills is a Brighton-based artist who designed The Fossil Tree, one of the four Millennium Milepost designs. Fossil imagery gives the cast-iron sculpture its distinctive character.",
        "Welsh artist Andrew Rowe designed a Millennium Milepost inspired by Swansea’s maritime and industrial heritage. Its directional arms help people find their way along the network.",
        "Scottish sculptor Iain McColl created The Cockerel, one of the four Millennium Milepost designs commissioned for the National Cycle Network.",
        "Belfast artist David Dudgeon designed Tracks. The milepost’s imagery reflects the marks cyclists make in the landscape.",
        "Civil engineer John Grimshaw helped volunteers build the Bristol and Bath Railway Path and served as Sustrans’ Chief Engineer. He later helped establish Greenways and Cycleroutes to support community paths.",
        "Charlie Harrow is credited with conceiving the Millennium Time Trail, a puzzle experience on the National Cycle Network. A reproduced launch release describes him as an artist and teacher whose aim was to encourage families to explore by bicycle."};
    static String designerContext(int design){return design<4?BIOS[design]:
            "Millennium Mileposts were created for the National Cycle Network to mark the year 2000.";}
    static String relatedAward(int index){return index==5?"The seven-chapter mystery":NAMES[index];}
    static int stage(int count){return Math.max(0,Math.min(7,count));}
    static boolean resolved(int stage,int position){
        for(int n=1;n<=stage(stage);n++)for(int p:POSITIONS[n])if(p==position)return true;
        return false;
    }
    static String cipher(int stage){
        if(stage(stage)==0)return "";
        StringBuilder out=new StringBuilder();
        for(int p=0;p<13;p++){
            if(p==7)out.append(' ');
            char target=TARGET.charAt(p);
            out.append(resolved(stage,p)?target:(char)('A'+(target-'A'+7)%26));
        }
        return out.toString();
    }
    /** During motion only unresolved positions scramble; settled state is versioned and deterministic. */
    static String animatedCipher(int previous,int target,float fraction){
        if(target<=0)return "";
        if(fraction>=0.78f)return cipher(target);
        StringBuilder out=new StringBuilder();int frame=Math.min(15,(int)(fraction*20));
        for(int p=0;p<13;p++){
            if(p==7)out.append(' ');
            char letter=TARGET.charAt(p);
            out.append(resolved(previous,p)?letter:(char)('A'+(letter-'A'+7+frame)%26));
        }
        return out.toString();
    }
    static List<String> layers(int stage){
        int n=stage(stage);List<String> layers=new ArrayList<>();
        if(n<7)for(int i=1;i<=n;i++)layers.add("harrow-layer-0"+i+"-"+THEMES[i].toLowerCase(Locale.ROOT).replace(' ','-'));
        return Collections.unmodifiableList(layers);
    }
    static String announcement(int stage){
        int n=stage(stage);
        if(n==0)return "Mystery reveal. No discoveries revealed.";
        if(n==7)return "Discovery 7 of 7. Charlie Harrow revealed.";
        return "Discovery "+n+" of 7. "+THEMES[n]+" layer revealed. "+(n*2)+" letters resolved.";
    }
    static final class Award {
        final boolean earned;final long ordinal;final String date,precision;
        Award(boolean earned,long ordinal,String date,String precision){this.earned=earned;this.ordinal=ordinal;this.date=date;this.precision=precision;}
    }
    static Snapshot pending(){
        List<Award> awards=new ArrayList<>();
        for(int i=0;i<7;i++)awards.add(new Award(false,0,null,null));
        return new Snapshot(false,0,0,0,0,0,0,"assessment-pending",null,null,awards);
    }
    static final class Snapshot {
        final boolean current;final int count,newPosts,pending,incomplete;final long postOrdinal,awardOrdinal;
        final String receipt,from,to;final List<Award> awards;
        Snapshot(boolean current,int count,int newPosts,int pending,int incomplete,long postOrdinal,long awardOrdinal,
                 String receipt,String from,String to,List<Award> awards){
            if(awards.size()!=7)throw new IllegalArgumentException("Seven states required");
            this.current=current;this.count=count;this.newPosts=newPosts;this.pending=pending;this.incomplete=incomplete;
            this.postOrdinal=postOrdinal;this.awardOrdinal=awardOrdinal;this.receipt=receipt;this.from=from;this.to=to;
            this.awards=Collections.unmodifiableList(new ArrayList<>(awards));
        }
        boolean introduced(){for(int i=0;i<6;i++)if(awards.get(i).ordinal>0)return true;return false;}
        int individuals(){int n=0;for(int i=0;i<6;i++)if(awards.get(i).earned)n++;return n;}
        int target(){return stage(count);}
        Card card(int index){
            Award a=awards.get(index);boolean visible=a.earned;
            return new Card(visible?NAMES[index]:introduced()?"Mystery personality":"?",
                visible&&index<6?BIOS[index]:"",visible?"Revealed":a.ordinal>0?"Previously unlocked":"Discover Mileposts to reveal more",
                visible,index==5&&introduced()?target():0);
        }
        String dates(){return from==null?"Historical dates unavailable":
                "Journey dates: "+from+(from.equals(to)?"":" to "+to)+". Journey precision; exact passing times unavailable.";}
        List<String> newAwards(long acknowledged){
            List<String> result=new ArrayList<>();
            if(current)for(int i=0;i<7;i++)if(awards.get(i).earned&&awards.get(i).ordinal>acknowledged)result.add(NAMES[i]);
            return result;
        }
    }
    static final class Card {
        final String title,biography,status;final boolean earned;final int stage;
        Card(String title,String biography,String status,boolean earned,int stage){this.title=title;this.biography=biography;this.status=status;this.earned=earned;this.stage=stage;}
        String accessible(){return title+". "+status+(biography.isEmpty()?"":". "+biography);}
    }
    private MilepostPresentation(){}
}
