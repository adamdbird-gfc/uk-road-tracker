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
    private Dialog celebrationDialog;
    private TextView celebrationIcon;
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
        long revision=JourneyStore.dataRevision(this);
        int highStreetRevision=AchievementStore.highStreetEvidenceRevision(this);
        if (shownRevision != Long.MIN_VALUE
                && (revision != shownRevision || highStreetRevision != shownHighStreetEvidenceRevision))
            loadAchievements();
    }

    @Override protected void onDestroy() {
        if (celebrationDialog != null && celebrationDialog.isShowing()) {
            celebrationDialog.dismiss();
        }
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
        header.setPadding(dp(18),dp(17),dp(18),dp(13));
        LinearLayout brand=RoadprintsHeader.create(this);
        brand.setPadding(0,0,0,dp(22));
        header.addView(brand);
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
        View bottomNavigation=buildBottomNavigation();
        root.addView(bottomNavigation);
        setContentView(root);
        applySystemBarInsets(root,header,bottomNavigation);
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

        celebrationIcon=text("★",32,0xFF735300,true);
        celebrationIcon.setGravity(Gravity.CENTER);
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
        celebrationIcon.setText(definition.icon==null||definition.icon.isEmpty()?"★":definition.icon);
        celebrationTitle.setText(definition.title);
        celebrationDescription.setText(definition.description==null?"":definition.description);
        celebrationDetail.setText(definition.detail==null?"":definition.detail);
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
