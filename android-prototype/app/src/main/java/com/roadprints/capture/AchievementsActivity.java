package com.roadprints.capture;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native version of the POC Achievements screen. */
public class AchievementsActivity extends Activity {
    private static final int NAVY=0xFF0B1C50;
    private static final int NAV_BAR=0xFF10275D;
    private static final int PANEL=0xFF182F62;
    private static final int CARD=0xFF233B78;
    private static final int MUTED=0xFFB9C5D8;
    private static final int GOLD=0xFFF7C450;
    private static final int TEAL=0xFF67D5CC;
    private static final int GREEN=0xFF77D6A3;
    private static final Object SNAPSHOT_CACHE_LOCK=new Object();
    private static AchievementStore.Snapshot processSnapshot;
    private static long processSnapshotRevision=Long.MIN_VALUE;
    private static int processHighStreetRevision=Integer.MIN_VALUE;
    private static long processStationRevision=Long.MIN_VALUE;

    private static final String SNAPSHOT_PREFS="roadprints_achievement_snapshot";
    private static java.util.concurrent.Executor achievementExecutor=ScreenDataLoader::execute;
    private static java.util.function.Function<android.content.Context,AchievementStore.Snapshot> achievementCalculator=AchievementStore::calculate;
    private static boolean loadInFlight;
    private static int cacheEpoch;
    private static final List<java.lang.ref.WeakReference<AchievementsActivity>> waitingScreens=new ArrayList<>();
    private AchievementStore.Snapshot shownSnapshot;
    private boolean shownKilometres;

    private final List<AchievementStore.Definition> celebrationQueue=new ArrayList<>();
    private LinearLayout content;
    private ScrollView scroll;
    private TextView summary;
    private long shownRevision=Long.MIN_VALUE;
    private int shownHighStreetEvidenceRevision=Integer.MIN_VALUE;
    private long shownServiceStationRevision=Long.MIN_VALUE;
    private boolean stationBackfillRequested;
    private int celebrationIndex;
    private Dialog celebrationDialog;
    private android.widget.ImageView celebrationIcon;
    private TextView celebrationTitle;
    private TextView celebrationDescription;
    private TextView celebrationDetail;
    private TextView celebrationPosition;
    private Button celebrationPrevious;
    private Button celebrationNext;
    private Button celebrationClaim;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAV_BAR);
        buildScreen();
        loadAchievements();
    }

    @Override protected void onResume() {
        super.onResume();
        evidenceRefreshHandler.removeCallbacks(evidenceRefresh);
        evidenceRefreshHandler.postDelayed(evidenceRefresh,2000);
        if(shownSnapshot!=null && shownKilometres!=DistanceUnits.usesKilometres(this))render(shownSnapshot);
        long revision=JourneyStore.dataRevision(this);
        int highStreetRevision=AchievementStore.highStreetEvidenceRevision(this);
        if (shownRevision != Long.MIN_VALUE
                && (revision != shownRevision || highStreetRevision != shownHighStreetEvidenceRevision
                || ServiceStationStore.revision(this) != shownServiceStationRevision))
            loadAchievements();
    }

    private final Handler evidenceRefreshHandler=new Handler(Looper.getMainLooper());
    private final Runnable evidenceRefresh=new Runnable() {
        @Override public void run() {
            if(shownRevision!=Long.MIN_VALUE && (JourneyStore.dataRevision(AchievementsActivity.this)!=shownRevision
                    ||AchievementStore.highStreetEvidenceRevision(AchievementsActivity.this)!=shownHighStreetEvidenceRevision
                    ||ServiceStationStore.revision(AchievementsActivity.this)!=shownServiceStationRevision))loadAchievements();
            evidenceRefreshHandler.postDelayed(this,2000);
        }
    };

    @Override protected void onPause() {
        evidenceRefreshHandler.removeCallbacks(evidenceRefresh);
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (celebrationDialog != null && celebrationDialog.isShowing()) {
            celebrationDialog.dismiss();
        }
        super.onDestroy();
    }

    private void buildScreen() {
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NAVY);

        LinearLayout header=new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18),dp(17),dp(18),dp(13));
        LinearLayout brand=RoadprintsHeader.create(this);
        brand.setPadding(0,0,0,dp(22));
        header.addView(brand);
        TextView eyebrow=text("YOUR TRAVEL RECORD",12,TEAL,true);
        TextView title=text("Achievements",28,Color.WHITE,true);
        summary=text("Your achievements",14,MUTED,false);
        summary.setPadding(0,dp(5),0,0);
        header.addView(eyebrow);
        header.addView(title);
        header.addView(summary);
        root.addView(header);

        scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18),dp(2),dp(18),dp(18));
        scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        View bottomNavigation=buildBottomNavigation();
        root.addView(GrowingStatusControl.create(this));
        root.addView(bottomNavigation);
        setContentView(root);
        applySystemBarInsets(root,header,bottomNavigation);
        renderInitialSnapshot();
    }

    private void renderInitialSnapshot() {
        AchievementStore.Snapshot cached;
        synchronized(SNAPSHOT_CACHE_LOCK) { cached=processSnapshot; }
        if(cached==null) {
            try {
                String saved=getSharedPreferences(SNAPSHOT_PREFS,MODE_PRIVATE).getString("snapshot",null);
                if(saved!=null && saved.length()<=256*1024) {
                    JSONObject root=new JSONObject(saved);
                    if(root.optInt("format")==1) {
                        cached=snapshotFromJson(root);
                        if(cached!=null) synchronized(SNAPSHOT_CACHE_LOCK) {
                            processSnapshot=cached;
                            processSnapshotRevision=root.optInt("catalogue")==AchievementStore.CATALOGUE_VERSION?root.optLong("journeys",Long.MIN_VALUE):Long.MIN_VALUE;
                            processHighStreetRevision=root.optInt("highStreets",Integer.MIN_VALUE);
                            processStationRevision=root.optLong("stations",Long.MIN_VALUE);
                        }
                    }
                }
            } catch(Exception ignored) { }
        }
        render(cached==null?AchievementStore.catalogueSnapshot(this):cached);
    }

    private void loadAchievements() {
        if (!stationBackfillRequested && ServiceStationStore.unlocked(this)
                && !ServiceStationStore.historicalBackfillComplete(this)) {
            stationBackfillRequested=true;
            ServiceStationStore.ensureHistoricalVisits(this,()->{
                if(!isFinishing() && !isDestroyed()) loadAchievements();
            });
        }
        final long revision=JourneyStore.dataRevision(this);
        final int highStreetRevision=AchievementStore.highStreetEvidenceRevision(this);
        final long stationRevision=ServiceStationStore.revision(this);
        final int epoch;
        synchronized(SNAPSHOT_CACHE_LOCK) {
            epoch=cacheEpoch;
            if(processSnapshot!=null) {
                if(shownSnapshot!=processSnapshot) render(processSnapshot);
                if(processSnapshotRevision==revision && processHighStreetRevision==highStreetRevision
                        && processStationRevision==stationRevision) {
                    shownRevision=revision;
                    shownHighStreetEvidenceRevision=highStreetRevision;
                    shownServiceStationRevision=stationRevision;
                    return;
                }
            }
            boolean waiting=false;
            for(java.lang.ref.WeakReference<AchievementsActivity> reference:waitingScreens)
                if(reference.get()==this) waiting=true;
            if(!waiting) waitingScreens.add(new java.lang.ref.WeakReference<>(this));
            if(loadInFlight) return;
            loadInFlight=true;
        }
        final android.content.Context appContext=getApplicationContext();
        achievementExecutor.execute(() -> {
            AchievementStore.Snapshot result=null;
            Exception failure=null;
            long cacheRevision=achievementCacheRevision(revision,highStreetRevision,stationRevision);
            try {
                // Migrate the previous exact-revision disk cache off the UI thread.
                result=readCachedSnapshot(appContext,cacheRevision);
                if(result==null) result=achievementCalculator.apply(appContext);
                JSONObject saved=snapshotToJson(result);
                if(saved!=null) {
                    saved.put("format",1).put("catalogue",AchievementStore.CATALOGUE_VERSION).put("journeys",revision)
                            .put("highStreets",highStreetRevision).put("stations",stationRevision);
                    synchronized(SNAPSHOT_CACHE_LOCK) {
                        if(epoch==cacheEpoch) appContext.getSharedPreferences(SNAPSHOT_PREFS,MODE_PRIVATE).edit()
                                .putString("snapshot",saved.toString()).apply();
                    }
                }
                synchronized(SNAPSHOT_CACHE_LOCK) {
                    if(epoch==cacheEpoch) {
                    processSnapshot=new AchievementStore.Snapshot(result.achievements,
                            java.util.Collections.emptyList(),result.dataRevision);
                    // Tag the inputs captured before calculation, never newer evidence.
                    processSnapshotRevision=revision;
                    processHighStreetRevision=highStreetRevision;
                    processStationRevision=stationRevision;
                    }
                }
            } catch(Exception error) {
                failure=error;
                android.util.Log.e("Roadprints","Achievement refresh failed",error);
            }
            final AchievementStore.Snapshot completed=result;
            final Exception error=failure;
            new Handler(Looper.getMainLooper()).post(() -> {
                List<AchievementsActivity> screens=new ArrayList<>();
                synchronized(SNAPSHOT_CACHE_LOCK) {
                    loadInFlight=false;
                    for(java.lang.ref.WeakReference<AchievementsActivity> reference:waitingScreens) {
                        AchievementsActivity screen=reference.get();
                        if(screen!=null && !screen.isFinishing() && !screen.isDestroyed()) screens.add(screen);
                    }
                    waitingScreens.clear();
                }
                if(epoch!=cacheEpoch) {
                    for(AchievementsActivity screen:screens) {
                        screen.renderInitialSnapshot();
                        screen.loadAchievements();
                    }
                    return;
                }
                boolean changed=JourneyStore.dataRevision(appContext)!=revision
                        || AchievementStore.highStreetEvidenceRevision(appContext)!=highStreetRevision
                        || ServiceStationStore.revision(appContext)!=stationRevision;
                for(AchievementsActivity screen:screens) {
                    if(error!=null || completed==null) {
                        screen.summary.setText("Progress refresh unavailable · tap to retry");
                        screen.summary.setOnClickListener(view -> screen.loadAchievements());
                    } else {
                        screen.summary.setOnClickListener(null);
                        screen.shownRevision=revision;
                        screen.shownHighStreetEvidenceRevision=highStreetRevision;
                        screen.shownServiceStationRevision=stationRevision;
                        screen.render(processSnapshot);
                    }
                }
                if(error==null && completed!=null && !screens.isEmpty()) {
                    // One carousel for this refresh, even after rapid tab reopening.
                    screens.get(screens.size()-1).showCelebrations(completed.newlyUnlocked);
                    if(changed) for(AchievementsActivity screen:screens) screen.loadAchievements();
                }
            });
        });
    }

    static void clearCachedAchievements(android.content.Context context) {
        synchronized(SNAPSHOT_CACHE_LOCK) {
            cacheEpoch++;
            processSnapshot=null;
            processSnapshotRevision=Long.MIN_VALUE;
            processHighStreetRevision=Integer.MIN_VALUE;
            processStationRevision=Long.MIN_VALUE;
            context.getSharedPreferences(SNAPSHOT_PREFS,MODE_PRIVATE).edit().clear().apply();
        }
    }

    private static long achievementCacheRevision(long journeys,int highStreets,long stations) {
        return journeys*1_000_003L+highStreets*31L+stations+AchievementStore.CATALOGUE_VERSION*9_999_991L;
    }

    private static AchievementStore.Snapshot readCachedSnapshot(android.content.Context context,long revision) {
        return snapshotFromJson(PersistentScreenCache.read(context,"achievements",revision));
    }

    private static AchievementStore.Snapshot snapshotFromJson(JSONObject root) {
        JSONArray rows=root==null?null:root.optJSONArray("items");
        if(rows==null)return null;
        List<AchievementStore.Progress> output=new ArrayList<>();
        java.util.Map<String,JSONObject> saved=new java.util.HashMap<>();
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.optJSONObject(i);
            if(row!=null) saved.put(row.optString("id"),row);
        }
        for(AchievementStore.Definition definition:AchievementStore.definitions()) {
            JSONObject row=saved.get(definition.id);
            if(row==null) {
                output.add(new AchievementStore.Progress(definition,0,0,(int)definition.target,
                        false,definition.description,null));
                continue;
            }
            JSONArray flags=row.optJSONArray("crossings");
            boolean[] crossings=null;
            if(flags!=null){crossings=new boolean[AchievementStore.crossings().size()];for(int c=0;c<crossings.length;c++)crossings[c]=flags.optBoolean(c);}
            AchievementStore.Progress progress=new AchievementStore.Progress(definition,row.optDouble("value"),
                    row.optInt("completed"),row.optInt("total"),row.optBoolean("unlocked"),row.optString("display"),crossings);
            progress.level=row.optInt("level",progress.unlocked?1:0);
            progress.contributions=stringList(row.optJSONArray("contributions"));
            progress.milestones=stringList(row.optJSONArray("milestones"));
            output.add(progress);
        }
        return output.isEmpty()?null:new AchievementStore.Snapshot(output,
                java.util.Collections.emptyList(),root.optLong("journeys",Long.MIN_VALUE));
    }

    private static List<String> stringList(JSONArray values) {
        List<String> result=new ArrayList<>();if(values!=null)for(int i=0;i<values.length();i++)result.add(values.optString(i));return result;
    }

    private static JSONObject snapshotToJson(AchievementStore.Snapshot snapshot) {
        try {
            JSONArray rows=new JSONArray();
            for(AchievementStore.Progress item:snapshot.achievements) {
                JSONArray flags=null;
                if(item.crossingProgress!=null){flags=new JSONArray();for(boolean flag:item.crossingProgress)flags.put(flag);}
                rows.put(new JSONObject().put("id",item.definition.id).put("value",item.value)
                        .put("completed",item.completed).put("total",item.total)
                        .put("unlocked",item.unlocked).put("display",item.display)
                        .put("crossings",flags==null?JSONObject.NULL:flags).put("level",item.level)
                        .put("contributions",new JSONArray("the-knowledge".equals(item.definition.id)?item.contributions.subList(0,Math.min(50,item.contributions.size())):item.contributions)).put("milestones",new JSONArray(item.milestones)));
            }
            return new JSONObject().put("items",rows);
        } catch (org.json.JSONException ignored) { return null; }
    }

    private void render(AchievementStore.Snapshot snapshot) {
        shownSnapshot=snapshot;
        shownKilometres=DistanceUnits.usesKilometres(this);
        int savedScroll=scroll.getScrollY();
        content.removeAllViews();
        boolean showServices=ServiceStationStore.unlocked(this);
        List<AchievementStore.Progress> visible=new ArrayList<>();
        int unlockedCount=0;
        for(AchievementStore.Progress item:snapshot.achievements) {
            boolean service="service-station".equals(item.definition.type);
            if(service&&!showServices) continue;
            visible.add(item);
            if(item.unlocked) unlockedCount++;
        }
        summary.setText(unlockedCount+" of "+visible.size()+" unlocked");
        LinearLayout overview=panel();
        TextView overviewTitle=text("Your milestones",18,Color.WHITE,true);
        TextView overviewCopy=text("Your recordings, distance and discoveries. Each family grows with your progress.",13,MUTED,false);
        overview.addView(overviewTitle);
        overviewCopy.setPadding(0,dp(5),0,dp(12));
        overview.addView(overviewCopy);
        ProgressBar overall=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        overall.setMax(Math.max(1,visible.size()));
        overall.setProgress(unlockedCount);
        overall.setProgressTintList(android.content.res.ColorStateList.valueOf(GOLD));
        overall.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF43598A));
        overview.addView(overall,new LinearLayout.LayoutParams(-1,dp(7)));
        content.addView(overview);
        String[] groups={"App milestones","Distance","Road discovery","Town exploration","Landmarks","Crossings"};
        for(String group:groups) {
            boolean heading=false;
            for(AchievementStore.Progress item:visible) {
                if(!group.equals(achievementGroup(item.definition))) continue;
                if(!heading) { content.addView(sectionHeading(group)); heading=true; }
                if(!"road-completion".equals(item.definition.type))content.addView(achievementCard(item));
            }
        }
        addRoadCompletionSection(visible,"Motorway completion","completion-motorway-");
        addRoadCompletionSection(visible,"A-road completion","completion-aroad-");
        if(showServices) {
            content.addView(sectionHeading("Service station achievements"));
            for(AchievementStore.Progress item:visible)
                if("service-station".equals(item.definition.type)) content.addView(achievementCard(item));
        }
        content.addView(text("Totals include imported and recorded travel. Coverage uses matched sections; road-name challenges update as town lookups complete.",
                12,MUTED,false));
        scroll.post(() -> scroll.scrollTo(0,savedScroll));
    }

    private static String achievementGroup(AchievementStore.Definition definition) {
        if("service-station".equals(definition.type)) return "Collections";
        if("town-exploration".equals(definition.type))return "Town exploration";
        if("app-use".equals(definition.type))return "App milestones";
        if("distance".equals(definition.type)||"long-journey".equals(definition.type))return "Distance";
        if("crossing-set".equals(definition.type)) return "Crossings";
        if("a-road-landmark".equals(definition.type) || "summit".equals(definition.type)) return "Landmarks";
        return "Road discovery";
    }

    private void addRoadCompletionSection(List<AchievementStore.Progress> visible,String name,String prefix) {
        int earned=0,count=0;
        for(AchievementStore.Progress p:visible)if(p.definition.id.startsWith(prefix)){count++;if(p.unlocked)earned++;}
        TextView toggle=sectionHeading(name+" · "+earned+" / "+count+" ▾");
        content.addView(toggle);
        LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);rows.setVisibility(View.GONE);content.addView(rows);
        toggle.setOnClickListener(v->{
            boolean open=rows.getVisibility()!=View.VISIBLE;
            if(open&&rows.getChildCount()==0)for(AchievementStore.Progress p:visible)if(p.definition.id.startsWith(prefix))rows.addView(achievementCard(p));
            rows.setVisibility(open?View.VISIBLE:View.GONE);
        });
    }

    private TextView sectionHeading(String label) {
        TextView heading=text(label,18,TEAL,true);
        heading.setPadding(dp(3),dp(14),dp(3),dp(8));
        return heading;
    }

    private View achievementCard(AchievementStore.Progress progress) {
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15),dp(14),dp(15),dp(13));
        card.setBackground(roundRect(progress.unlocked?0xFF3C3D3A:CARD,
                progress.unlocked?0xFFC9A24A:0xFF46649E,dp(16)));
        card.setElevation(dp(1));
        LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(-1,-2);
        cardParams.bottomMargin=dp(9);
        card.setLayoutParams(cardParams);

        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.ImageView badge=RoadprintsIcons.image(this, RoadprintsIcons.achievement(progress.definition.id), progress.unlocked?GOLD:MUTED, progress.definition.title + (progress.unlocked?", unlocked":", locked"));
        badge.setPadding(dp(10),dp(10),dp(10),dp(10));
        badge.setBackground(roundRect(progress.unlocked?0xFF5B4C2F:0xFF344A7B,0x00446699,dp(28)));
        row.addView(badge,new LinearLayout.LayoutParams(dp(48),dp(48)));

        LinearLayout copy=new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12),0,0,0);
        TextView status=text(progress.definition.levels!=null?"LEVEL "+progress.level+" / "+progress.definition.levels.length:(progress.unlocked?"UNLOCKED":"NEXT MILESTONE"),10,
                progress.unlocked?GOLD:TEAL,true);
        TextView name=text(AchievementStore.titleFor(progress.definition,progress.level),16,Color.WHITE,true);
        String display="distance".equals(progress.definition.type)||progress.definition.levels!=null
                || "road-count".equals(progress.definition.type)||"high-street-settlement".equals(progress.definition.type)
                ||"app-use".equals(progress.definition.type)||"long-journey".equals(progress.definition.type)?AchievementStore.progressText(this,progress):progress.display;
        TextView detail=text(display,13,MUTED,false);
        detail.setPadding(0,dp(3),0,0);
        copy.addView(status); copy.addView(name); copy.addView(detail);
        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        card.addView(row);
        if("the-knowledge".equals(progress.definition.id)||"mary-high-streets".equals(progress.definition.id)
                ||"mastered-monopoly".equals(progress.definition.id)||"distance".equals(progress.definition.type)||"town-exploration".equals(progress.definition.type)) {
            TextView requirement=text(progress.definition.description,12,MUTED,false);
            requirement.setPadding(dp(60),dp(7),0,0);card.addView(requirement);
        }
        if(!progress.contributions.isEmpty()||!progress.milestones.isEmpty()) {
            TextView toggle=text("View contributions and milestones ▾",12,TEAL,true);toggle.setPadding(dp(60),dp(10),0,0);
            LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setVisibility(View.GONE);
            toggle.setOnClickListener(v->{
                boolean open=list.getVisibility()!=View.VISIBLE;
                if(open&&list.getChildCount()==0) {
                    for(String milestone:progress.milestones){TextView t=text(milestone,12,GOLD,false);t.setPadding(dp(60),dp(5),0,0);list.addView(t);}
                    int shown=0;for(String contribution:progress.contributions){if(shown++>=50)break;TextView t=text(("town-exploration".equals(progress.definition.type)?"":"✓ ")+contribution,12,contribution.startsWith("○")?MUTED:GREEN,false);t.setPadding(dp(60),dp(5),0,0);list.addView(t);}
                    if(progress.contributions.size()>50)list.addView(text("First 50 shown · "+progress.contributions.size()+" total",12,MUTED,false));
                }
                list.setVisibility(open?View.VISIBLE:View.GONE);
            });card.addView(toggle);card.addView(list);
        }

        if ("crossing-set".equals(progress.definition.type)) {
            TextView toggle=text("View crossing checklist  ▾",12,TEAL,true);
            toggle.setPadding(dp(60),dp(11),0,0);
            LinearLayout checklist=new LinearLayout(this);
            checklist.setOrientation(LinearLayout.VERTICAL);
            checklist.setVisibility(View.GONE);
            boolean[] checked=progress.crossingProgress == null
                    ? new boolean[AchievementStore.crossings().size()] : progress.crossingProgress;
            for (int index=0; index<AchievementStore.crossings().size(); index++) {
                AchievementStore.Crossing crossing=AchievementStore.crossings().get(index);
                TextView item=text((checked[index]?"✓  ":"○  ")+crossing.title+" · "+crossing.hint,
                        12,checked[index]?GREEN:MUTED,false);
                item.setPadding(dp(60),dp(5),0,dp(2));
                checklist.addView(item);
            }
            toggle.setOnClickListener(view -> {
                boolean open=checklist.getVisibility()!=View.VISIBLE;
                checklist.setVisibility(open?View.VISIBLE:View.GONE);
                toggle.setText(open?"Hide crossing checklist  ▴":"View crossing checklist  ▾");
            });
            card.addView(toggle); card.addView(checklist);
        } else if ("network-percent".equals(progress.definition.type)
                || "high-street-settlement".equals(progress.definition.type) || progress.definition.levels!=null || "road-count".equals(progress.definition.type) || "picasso".equals(progress.definition.id)) {
            ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
            int maximum=1000;
            double target=AchievementStore.nextTarget(progress.definition,progress.value);
            bar.setMax(maximum);
            bar.setProgress((int)Math.min(maximum,Math.max(0,progress.value/Math.max(1,target)*maximum)));
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(progress.unlocked?GOLD:TEAL));
            bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF43598A));
            LinearLayout.LayoutParams barParams=new LinearLayout.LayoutParams(-1,dp(5));
            barParams.setMargins(dp(60),dp(11),0,0);
            card.addView(bar,barParams);
        }
        return card;
    }

    private void showCelebrations(List<AchievementStore.Definition> definitions) {
        if (definitions == null || definitions.isEmpty() || isFinishing()) return;
        celebrationQueue.clear();
        celebrationQueue.addAll(definitions);
        celebrationIndex=0;
        buildCelebrationDialog();
        renderCelebration();
        celebrationDialog.show();
        Window window=celebrationDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params=window.getAttributes();
            params.dimAmount=0.65f;
            window.setAttributes(params);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    private void buildCelebrationDialog() {
        if (celebrationDialog != null) return;
        FrameLayout overlay=new FrameLayout(this);
        overlay.setBackgroundColor(0xB8071122);
        overlay.setOnClickListener(view -> closeCelebrations());

        LinearLayout card=new LinearLayout(this) {
            private float downX,downY;
            @Override public boolean onInterceptTouchEvent(MotionEvent event) {
                if (event.getActionMasked()==MotionEvent.ACTION_DOWN) {
                    downX=event.getX(); downY=event.getY();
                } else if (event.getActionMasked()==MotionEvent.ACTION_MOVE) {
                    float dx=event.getX()-downX,dy=event.getY()-downY;
                    if (Math.abs(dx)>dp(48) && Math.abs(dx)>Math.abs(dy)) return true;
                }
                return super.onInterceptTouchEvent(event);
            }
            @Override public boolean onTouchEvent(MotionEvent event) {
                if (event.getActionMasked()==MotionEvent.ACTION_UP) {
                    float dx=event.getX()-downX,dy=event.getY()-downY;
                    if (Math.abs(dx)>dp(48) && Math.abs(dx)>Math.abs(dy)) {
                        moveCelebration(dx<0?1:-1);
                        return true;
                    }
                }
                return super.onTouchEvent(event);
            }
        };
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(24),dp(26),dp(24),dp(22));
        card.setBackground(roundRect(0xFFFFFDF8,0xFFC9A24A,dp(22)));
        card.setElevation(dp(16));
        card.setClickable(true);
        card.setOnClickListener(view -> { });

        celebrationIcon=RoadprintsIcons.image(this,R.drawable.ic_roadprints_star,0xFF0B1C50,"Achievement unlocked");
        celebrationIcon.setPadding(dp(16),dp(16),dp(16),dp(16));
        celebrationIcon.setBackground(roundRect(0xFFF2B544,0xFFF2B544,dp(40)));
        card.addView(celebrationIcon,new LinearLayout.LayoutParams(dp(68),dp(68)));

        TextView eyebrow=text("ACHIEVEMENT UNLOCKED",12,0xFF735300,true);
        eyebrow.setPadding(0,dp(17),0,dp(4));
        card.addView(eyebrow);

        celebrationTitle=text("Achievement",24,0xFF172033,true);
        celebrationTitle.setGravity(Gravity.CENTER);
        card.addView(celebrationTitle);

        celebrationDescription=text("",16,0xFF25324A,false);
        celebrationDescription.setGravity(Gravity.CENTER);
        celebrationDescription.setPadding(0,dp(9),0,0);
        card.addView(celebrationDescription);

        celebrationDetail=text("",14,0xFF657083,false);
        celebrationDetail.setGravity(Gravity.CENTER);
        celebrationDetail.setPadding(0,dp(6),0,0);
        card.addView(celebrationDetail);

        LinearLayout carousel=new LinearLayout(this);
        carousel.setGravity(Gravity.CENTER);
        carousel.setPadding(0,dp(12),0,0);
        celebrationPrevious=celebrationArrow("←","Previous achievement");
        celebrationPosition=text("",13,0xFF536078,true);
        celebrationPosition.setGravity(Gravity.CENTER);
        celebrationPosition.setMinWidth(dp(60));
        celebrationNext=celebrationArrow("→","Next achievement");
        carousel.addView(celebrationPrevious);
        carousel.addView(celebrationPosition,new LinearLayout.LayoutParams(dp(72),dp(42)));
        carousel.addView(celebrationNext);
        card.addView(carousel);

        celebrationClaim=new Button(this);
        celebrationClaim.setTextColor(Color.WHITE);
        celebrationClaim.setTextSize(15);
        celebrationClaim.setTypeface(null,Typeface.BOLD);
        celebrationClaim.setAllCaps(false);
        celebrationClaim.setMinHeight(dp(52));
        celebrationClaim.setBackground(roundRect(NAVY,NAVY,dp(12)));
        LinearLayout.LayoutParams claimParams=new LinearLayout.LayoutParams(-1,dp(52));
        claimParams.topMargin=dp(14);
        card.addView(celebrationClaim,claimParams);

        FrameLayout.LayoutParams cardParams=new FrameLayout.LayoutParams(
                Math.min(dp(390),getResources().getDisplayMetrics().widthPixels-dp(36)),
                FrameLayout.LayoutParams.WRAP_CONTENT,Gravity.CENTER);
        overlay.addView(card,cardParams);
        celebrationDialog=new Dialog(this);
        celebrationDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        celebrationDialog.setContentView(overlay);
        celebrationDialog.setCancelable(true);
        celebrationDialog.setCanceledOnTouchOutside(false);
        celebrationDialog.setOnCancelListener(dialog -> clearCelebrationQueue());
        celebrationDialog.setOnDismissListener(dialog -> clearCelebrationQueue());
        celebrationPrevious.setOnClickListener(view -> moveCelebration(-1));
        celebrationNext.setOnClickListener(view -> moveCelebration(1));
        celebrationClaim.setOnClickListener(view -> closeCelebrations());
    }

    private Button celebrationArrow(String label,String description) {
        Button button=new Button(this);
        button.setText(label);
        button.setTextSize(20);
        button.setTextColor(NAVY);
        button.setContentDescription(description);
        button.setMinWidth(dp(48));
        button.setMinimumWidth(dp(48));
        button.setMinHeight(dp(42));
        button.setBackground(roundRect(0xFFEFF3F9,0xFFCBD4E4,dp(9)));
        return button;
    }

    private void renderCelebration() {
        if (celebrationQueue.isEmpty()) return;
        AchievementStore.Definition definition=celebrationQueue.get(celebrationIndex);
        celebrationIcon.setImageResource(RoadprintsIcons.achievement(definition.id));
        celebrationIcon.setContentDescription(definition.title);
        celebrationTitle.setText(definition.title);
        celebrationDescription.setText(definition.description==null?"":definition.description);
        celebrationDetail.setText(definition.detail==null?"":definition.detail);
        if(shownSnapshot!=null)for(AchievementStore.Progress progress:shownSnapshot.achievements)if(progress.definition.id.equals(definition.id)) {
            celebrationTitle.setText(AchievementStore.titleFor(definition,progress.level));
            celebrationDetail.setText((definition.levels==null?"":"Level "+progress.level+" / "+definition.levels.length+" · ")+AchievementStore.progressText(this,progress));
            if("long-journey".equals(definition.type))celebrationDescription.setText("Complete one journey of "+DistanceUnits.format(this,1609.344*10)+" on foot or "+DistanceUnits.format(this,1609.344*250)+" by road.");
            break;
        }
        boolean multiple=celebrationQueue.size()>1;
        int visibility=multiple?View.VISIBLE:View.GONE;
        celebrationPrevious.setVisibility(visibility);
        celebrationNext.setVisibility(visibility);
        celebrationPosition.setVisibility(visibility);
        celebrationPrevious.setEnabled(celebrationIndex>0);
        celebrationNext.setEnabled(celebrationIndex<celebrationQueue.size()-1);
        celebrationPrevious.setAlpha(celebrationIndex>0?1f:0.45f);
        celebrationNext.setAlpha(celebrationIndex<celebrationQueue.size()-1?1f:0.45f);
        celebrationPosition.setText(multiple
                ?(celebrationIndex+1)+" of "+celebrationQueue.size():"");
        celebrationClaim.setText(multiple&&celebrationIndex<celebrationQueue.size()-1
                ?"Claim all achievements":"Claim achievement");
    }

    private void moveCelebration(int offset) {
        if (celebrationQueue.isEmpty()) return;
        celebrationIndex=Math.max(0,Math.min(celebrationQueue.size()-1,celebrationIndex+offset));
        renderCelebration();
    }

    private void closeCelebrations() {
        if (celebrationDialog!=null && celebrationDialog.isShowing()) celebrationDialog.dismiss();
        clearCelebrationQueue();
    }

    private void clearCelebrationQueue() {
        celebrationQueue.clear();
        celebrationIndex=0;
    }

    private LinearLayout panel() {
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(17),dp(16),dp(17),dp(15));
        panel.setBackground(roundRect(PANEL,0xFF354D7D,dp(18)));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.bottomMargin=dp(10);
        panel.setLayoutParams(params);
        return panel;
    }

    private View buildBottomNavigation() {
        LinearLayout nav=new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        nav.setPadding(dp(8),0,dp(8),0);
        nav.setBackgroundColor(NAV_BAR);
        int[] icons={R.drawable.ic_nav_map,R.drawable.ic_nav_journeys,
                R.drawable.ic_nav_progress,R.drawable.ic_nav_achievements,
                R.drawable.ic_nav_collections};
        String[] labels={"Map","Journeys","Progress","Achievements","Collections"};
        for (int index=0; index<labels.length; index++) {
            final int selected=index;
            LinearLayout item=new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
            item.setPadding(0,dp(4),0,0);
            ImageView icon=new ImageView(this);
            icon.setImageResource(icons[index]);
            icon.setContentDescription(labels[index]);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            icon.setColorFilter(index==3?GOLD:MUTED,android.graphics.PorterDuff.Mode.SRC_IN);
            item.addView(icon,new LinearLayout.LayoutParams(-1,dp(32)));
            TextView label=text(labels[index],10,index==3?GOLD:MUTED,false);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            item.addView(label,new LinearLayout.LayoutParams(-1,dp(24)));
            if (index<=4) {
                item.setClickable(true); item.setFocusable(true);
                item.setOnClickListener(view -> navigate(selected));
            } else item.setAlpha(0.55f);
            nav.addView(item,new LinearLayout.LayoutParams(0,dp(68),1));
        }
        return nav;
    }

    private void applySystemBarInsets(View root,View header,View bottomNavigation) {
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.R) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
                top=bars.top;
                bottom=bars.bottom;
            } else {
                top=insets.getSystemWindowInsetTop();
                bottom=insets.getSystemWindowInsetBottom();
            }
            header.setPadding(dp(18),dp(17)+top,dp(18),dp(13));
            LinearLayout.LayoutParams navParams=(LinearLayout.LayoutParams)
                    bottomNavigation.getLayoutParams();
            navParams.height=dp(68)+bottom;
            bottomNavigation.setPadding(dp(8),0,dp(8),bottom);
            bottomNavigation.setLayoutParams(navParams);
            return insets;
        });
        root.requestApplyInsets();
    }

    private void navigate(int index) {
        if (index==3) return;
        Class<?> target=index==0?MapActivity.class:index==1?JourneyListActivity.class
                :index==2?ProgressActivity.class:index==4?CollectionsActivity.class:AchievementsActivity.class;
        startActivity(new Intent(this,target));
        finish();
    }

    private TextView text(String value,float size,int color,boolean bold) {
        TextView view=new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(null,Typeface.BOLD);
        return view;
    }

    private GradientDrawable roundRect(int fill,int stroke,int radius) {
        GradientDrawable drawable=new GradientDrawable();
        drawable.setColor(fill); drawable.setCornerRadius(radius);
        if (stroke!=0) drawable.setStroke(dp(1),stroke);
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value*getResources().getDisplayMetrics().density);
    }
}
