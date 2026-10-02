package com.roadprints.capture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler mainHandler=new Handler(Looper.getMainLooper());
    private final List<AchievementStore.Definition> celebrationQueue=new ArrayList<>();
    private LinearLayout content;
    private ScrollView scroll;
    private TextView summary;
    private long shownRevision=Long.MIN_VALUE;
    private int shownHighStreetEvidenceRevision=Integer.MIN_VALUE;
    private int generation;
    private int celebrationIndex;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAV_BAR);
        buildScreen();
        loadAchievements();
    }

    @Override protected void onResume() {
        super.onResume();
        long revision=JourneyStore.dataRevision(this);
        int highStreetRevision=AchievementStore.highStreetEvidenceRevision(this);
        if (shownRevision != Long.MIN_VALUE
                && (revision != shownRevision || highStreetRevision != shownHighStreetEvidenceRevision))
            loadAchievements();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        generation++;
        worker.shutdownNow();
    }

    private void buildScreen() {
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NAVY);

        LinearLayout header=new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(22),dp(17),dp(22),dp(13));
        TextView eyebrow=text("YOUR TRAVEL RECORD",12,TEAL,true);
        TextView title=text("Achievements",28,Color.WHITE,true);
        summary=text("Checking your saved journeys…",14,MUTED,false);
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
        root.addView(buildBottomNavigation());
        setContentView(root);
        renderMessage("Checking saved journeys and coverage…",false);
    }

    private void loadAchievements() {
        int request=++generation;
        renderMessage("Checking saved journeys and coverage…",false);
        worker.execute(() -> {
            AchievementStore.Snapshot result;
            try { result=AchievementStore.calculate(getApplicationContext()); }
            catch (Exception error) {
                android.util.Log.e("Roadprints", "Achievements could not be calculated", error);
                mainHandler.post(() -> {
                    if (request == generation && !isFinishing())
                        renderMessage("Achievements could not be loaded. Your saved journeys are unchanged.",true);
                });
                return;
            }
            mainHandler.post(() -> {
                if (request != generation || isFinishing()) return;
                shownRevision=result.dataRevision;
                shownHighStreetEvidenceRevision=AchievementStore.highStreetEvidenceRevision(this);
                render(result);
                showCelebrations(result.newlyUnlocked);
            });
        });
    }

    private void render(AchievementStore.Snapshot snapshot) {
        content.removeAllViews();
        summary.setText(snapshot.unlockedCount()+" of "+snapshot.achievements.size()+" unlocked");
        LinearLayout overview=panel();
        TextView overviewTitle=text("Your milestones",18,Color.WHITE,true);
        TextView overviewCopy=text("Earned as your journeys reveal more of the road network.",13,MUTED,false);
        overview.addView(overviewTitle);
        overviewCopy.setPadding(0,dp(5),0,dp(12));
        overview.addView(overviewCopy);
        ProgressBar overall=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        overall.setMax(Math.max(1,snapshot.achievements.size()));
        overall.setProgress(snapshot.unlockedCount());
        overall.setProgressTintList(android.content.res.ColorStateList.valueOf(GOLD));
        overall.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF43598A));
        overview.addView(overall,new LinearLayout.LayoutParams(-1,dp(7)));
        content.addView(overview);

        for (AchievementStore.Progress progress : snapshot.achievements) {
            content.addView(achievementCard(progress));
        }
        content.addView(text("Achievements are calculated from saved UK driving and bus coverage, plus local-road settlement results.",
                12,MUTED,false));
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
        TextView badge=text(progress.unlocked?progress.definition.icon:"🔒",25,
                progress.unlocked?GOLD:MUTED,true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(roundRect(progress.unlocked?0xFF5B4C2F:0xFF344A7B,0x00446699,dp(28)));
        row.addView(badge,new LinearLayout.LayoutParams(dp(48),dp(48)));

        LinearLayout copy=new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12),0,0,0);
        TextView status=text(progress.unlocked?"UNLOCKED":"NEXT MILESTONE",10,
                progress.unlocked?GOLD:TEAL,true);
        TextView name=text(progress.definition.title,16,Color.WHITE,true);
        TextView detail=text(progress.display,13,MUTED,false);
        detail.setPadding(0,dp(3),0,0);
        copy.addView(status); copy.addView(name); copy.addView(detail);
        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        card.addView(row);

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
                || "high-street-settlement".equals(progress.definition.type)) {
            ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
            int maximum="network-percent".equals(progress.definition.type)?100:10;
            bar.setMax(maximum);
            bar.setProgress((int)Math.min(maximum,Math.max(0,progress.value)));
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(progress.unlocked?GOLD:TEAL));
            bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF43598A));
            LinearLayout.LayoutParams barParams=new LinearLayout.LayoutParams(-1,dp(5));
            barParams.setMargins(dp(60),dp(11),0,0);
            card.addView(bar,barParams);
        }
        return card;
    }

    private void showCelebrations(List<AchievementStore.Definition> definitions) {
        celebrationQueue.clear();
        celebrationQueue.addAll(definitions);
        celebrationIndex=0;
        showNextCelebration();
    }

    private void showNextCelebration() {
        if (celebrationIndex>=celebrationQueue.size() || isFinishing()) return;
        AchievementStore.Definition definition=celebrationQueue.get(celebrationIndex);
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("Achievement unlocked · "+definition.icon)
                .setMessage(definition.title+"\n\n"+definition.detail)
                .setPositiveButton(celebrationIndex<celebrationQueue.size()-1
                        ? "Claim all achievements" : "Claim achievement",(d,which)->{
                    celebrationIndex++;
                    showNextCelebration();
                })
                .create();
        dialog.show();
    }

    private void renderMessage(String message, boolean error) {
        content.removeAllViews();
        LinearLayout panel=panel();
        panel.addView(text(message,14,error?0xFFFFC1B8:MUTED,false));
        content.addView(panel);
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
            if (index<=3) {
                item.setClickable(true); item.setFocusable(true);
                item.setOnClickListener(view -> navigate(selected));
            } else item.setAlpha(0.55f);
            nav.addView(item,new LinearLayout.LayoutParams(0,dp(68),1));
        }
        return nav;
    }

    private void navigate(int index) {
        if (index==3) return;
        Class<?> target=index==0?MapActivity.class:index==1?JourneyListActivity.class:ProgressActivity.class;
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
