package com.roadprints.capture;

/** Departure/travel/pause/arrival evidence independent of Android activity callbacks. */
final class ContextJourneyModel {
    static final class Decision {
        final int boundary;
        final String nextMode,reason;
        Decision(int boundary,String nextMode,String reason){this.boundary=boundary;this.nextMode=nextMode;this.reason=reason;}
    }
    private long lastTime, pauseSince, proposalSince;
    private double lastLat,lastLon,anchorLat,anchorLon,path,proposalLat,proposalLon;
    private float proposalAccuracy;
    private float lastAccuracy;
    private int pauseIndex=-1,proposalIndex=-1,proposalSamples;
    private String proposal="";
    private ContextMap.Evidence pauseContext=new ContextMap.Evidence();
    Decision sample(String mode,int index,long time,double lat,double lon,float accuracy,float speed,
                    ContextMap.Evidence context,boolean atStation) {
        if(!Float.isFinite(accuracy)||accuracy<=0||accuracy>50||time<=lastTime)return null;
        double step=lastTime==0?0:RailStationCatalog.metres(lastLat,lastLon,lat,lon);
        long gap=time-lastTime;
        boolean measuredSpeed=Float.isFinite(speed)&&speed>=0;
        if(!measuredSpeed) {
            // Network jitter is not a vote against a pending GPS mode change.
            // Leave the previous trusted observation and proposal intact.
            return null;
        }
        boolean plausible=lastTime==0 || (gap>0&&step/gap*1000<80);
        float previousAccuracy=lastAccuracy;
        lastLat=lat;lastLon=lon;lastTime=time;lastAccuracy=accuracy;
        if(!plausible){clearProposal();return null;}
        if(speed<=.5) {
            if(pauseSince==0||gap>120000||RailStationCatalog.metres(anchorLat,anchorLon,lat,lon)>35+accuracy) {
                pauseSince=time;pauseIndex=index;anchorLat=lat;anchorLon=lon;
            }
            pauseContext=context;
            clearProposal();
            if(!atStation && !"train".equals(mode) && context.destination && accuracy<=25 && time-pauseSince>=(context.site?120000:60000))
                return new Decision(Math.max(1,pauseIndex),null,"arrival_"+context.kind);
            return null;
        }
        if(pauseSince!=0 && RailStationCatalog.metres(anchorLat,anchorLon,lat,lon)>35+accuracy) {
            // A new departure confirms the preceding stay. Ordinary road/rail
            // pauses remain one journey; unknown places require a longer dwell.
            long dwell=time-pauseSince;
            if(!atStation && !"train".equals(mode) && dwell>=(pauseContext.site?120000:60000) && pauseContext.destination) {
                Decision d=new Decision(Math.max(1,pauseIndex),mode,"departure_after_place_stay");pauseSince=0;clearProposal();return d;
            }
            pauseSince=0;pauseIndex=-1;
        }
        boolean foot="walking".equals(mode)||"running".equals(mode);
        String suggested="";
        if(foot && speed>=6) suggested="unknown";
        else if(!foot && !"cycling".equals(mode) && !"train".equals(mode) && speed>=.5 && speed<=2.5) suggested="walking";
        else if("walking".equals(mode) && speed>=2.6 && speed<=4.8) suggested="running";
        else if("running".equals(mode) && speed>=.5 && speed<=1.8) suggested="walking";
        else if("unknown".equals(mode) && context.road && !context.rail && speed>=6) suggested="driving";
        else if("unknown".equals(mode) && context.rail && !context.road && speed>=6) suggested="train";
        if(suggested.isEmpty()){clearProposal();return null;}
        if(!suggested.equals(proposal)||gap>60000) {
            proposal=suggested;proposalSince=time;proposalIndex=Math.max(1,index);proposalSamples=0;path=0;
            proposalLat=lat;proposalLon=lon;proposalAccuracy=accuracy;
        }
        if(proposalSamples>0)path+=Math.max(0,step-(accuracy+previousAccuracy)*.35);
        proposalSamples++;
        long duration=("running".equals(suggested)||"walking".equals(mode)&&"walking".equals(suggested))?60000:40000;
        if(proposalSamples<4 || time-proposalSince<duration || path<("walking".equals(suggested)?40:100))return null;
        // Jitter can accumulate path length inside a home or station. A mode
        // change also needs departure from the proposal's uncertainty area.
        double extent=RailStationCatalog.metres(proposalLat,proposalLon,lat,lon)-proposalAccuracy-accuracy;
        if(extent<("walking".equals(suggested)||"running".equals(suggested)?25:50))return null;
        // A station owns walking-to-vehicle handoffs until rail context resolves them.
        if(atStation && foot && "unknown".equals(suggested) && time-proposalSince<90000)return null;
        Decision d=new Decision(proposalIndex,suggested,"context_mode_"+suggested);clearProposal();return d;
    }
    boolean holdPause(String mode,long dwell,ContextMap.Evidence context) {
        return "train".equals(mode) || (!context.destination && (context.rail
                || context.road&&context.throughRoad&&!context.routeAmbiguous
                || ((context.road&&!context.available)||context.outdoor)&&dwell<900000));
    }
    private void clearProposal(){proposal="";proposalSince=0;proposalIndex=-1;proposalSamples=0;path=0;}
    String[] checkpoint() {
        return new String[]{"4",String.valueOf(lastTime),String.valueOf(lastLat),String.valueOf(lastLon),String.valueOf(lastAccuracy),
                String.valueOf(pauseSince),String.valueOf(pauseIndex),String.valueOf(anchorLat),String.valueOf(anchorLon),
                proposal,String.valueOf(proposalSince),String.valueOf(proposalIndex),String.valueOf(proposalSamples),String.valueOf(path),
                pauseContext.kind,String.valueOf(pauseContext.destination),String.valueOf(pauseContext.road),String.valueOf(pauseContext.rail),String.valueOf(pauseContext.outdoor),
                String.valueOf(proposalLat),String.valueOf(proposalLon),String.valueOf(proposalAccuracy),String.valueOf(pauseContext.throughRoad),String.valueOf(pauseContext.site)};
    }
    void restore(String[] state,int pointCount) {
        boolean old=state.length==19&&"1".equals(state[0]);
        boolean v2=state.length==22&&"2".equals(state[0]);
        boolean v3=state.length==23&&"3".equals(state[0]);
        boolean v4=state.length==24&&"4".equals(state[0]);
        if(!old&&!v2&&!v3&&!v4)return;
        try {
            if(Integer.parseInt(state[6])>=pointCount||Integer.parseInt(state[11])>=pointCount)return;
            lastTime=Long.parseLong(state[1]);lastLat=Double.parseDouble(state[2]);lastLon=Double.parseDouble(state[3]);lastAccuracy=Float.parseFloat(state[4]);
            pauseSince=Long.parseLong(state[5]);pauseIndex=Integer.parseInt(state[6]);anchorLat=Double.parseDouble(state[7]);anchorLon=Double.parseDouble(state[8]);
            proposal=state[9];proposalSince=Long.parseLong(state[10]);proposalIndex=Integer.parseInt(state[11]);proposalSamples=Integer.parseInt(state[12]);path=Double.parseDouble(state[13]);
            pauseContext.kind=state[14];pauseContext.destination=Boolean.parseBoolean(state[15]);pauseContext.road=Boolean.parseBoolean(state[16]);
            pauseContext.rail=Boolean.parseBoolean(state[17]);pauseContext.outdoor=Boolean.parseBoolean(state[18]);
            if(old)clearProposal(); // An old checkpoint lacks the transition origin.
            else {proposalLat=Double.parseDouble(state[19]);proposalLon=Double.parseDouble(state[20]);proposalAccuracy=Float.parseFloat(state[21]);}
            pauseContext.throughRoad=(v3||v4)&&Boolean.parseBoolean(state[22]);
            pauseContext.site=v4&&Boolean.parseBoolean(state[23]);
        } catch(RuntimeException ignored){lastTime=0;pauseSince=0;clearProposal();}
    }
}
