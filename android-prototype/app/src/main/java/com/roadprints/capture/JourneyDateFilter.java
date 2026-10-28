package com.roadprints.capture;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.json.JSONObject;

/** Local-calendar date ranges; the selected end date includes the whole day, even across DST. */
final class JourneyDateFilter {
    static final String[] VALUES={"all","today","week","month","custom"};
    static final String[] LABELS={"All dates","Today","Last 7 days","This month","Custom range"};
    final String preset;
    final LocalDate from,to;
    JourneyDateFilter(String preset,LocalDate from,LocalDate to){this.preset=preset;this.from=from;this.to=to;}
    static JourneyDateFilter all(){return new JourneyDateFilter("all",null,null);}
    boolean valid(){return !"custom".equals(preset)||(from!=null&&to!=null&&!to.isBefore(from));}
    boolean matches(JSONObject journey,LocalDate today,ZoneId zone) {
        if("all".equals(preset))return true;
        if(!valid())return false;
        LocalDate start,end;
        switch(preset){
            case "today":start=today;end=today;break;
            case "week":start=today.minusDays(6);end=today;break;
            case "month":start=today.withDayOfMonth(1);end=today;break;
            case "custom":start=from;end=to;break;
            default:return false;
        }
        try {
            LocalDate day=Instant.parse(journey.optString("started_at")).atZone(zone).toLocalDate();
            return !day.isBefore(start)&&!day.isAfter(end);
        }catch(Exception missingDate){return false;}
    }
    String label(){
        if("custom".equals(preset)&&valid()) {
            DateTimeFormatter format=DateTimeFormatter.ofPattern("d MMM yyyy",Locale.UK);
            return from.format(format)+(from.equals(to)?"":" – "+to.format(format));
        }
        for(int i=0;i<VALUES.length;i++)if(VALUES[i].equals(preset))return LABELS[i];
        return "All dates";
    }
    static JourneyDateFilter restore(String preset,String from,String to) {
        try {JourneyDateFilter value=new JourneyDateFilter(preset,from==null?null:LocalDate.parse(from),to==null?null:LocalDate.parse(to));
            for(String allowed:VALUES)if(allowed.equals(preset)&&value.valid())return value;
        }catch(Exception ignored) { }
        return all();
    }
}
