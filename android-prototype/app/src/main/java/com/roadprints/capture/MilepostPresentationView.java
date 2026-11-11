package com.roadprints.capture;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

/** Embeddable production experience. Entitlements are read-only; replay writes presentation only.
 * Accepts only a worker-prepared compact receipt. Lifecycle and capture suspend optional replay.
 * Static, explicit transitions are also the reduced-motion alternative; no timed task is needed. */
final class MilepostPresentationView extends ScrollView {
    private static final int NAVY=0xFF0B1C50,MUTED=0xFFB9C5D8,GOLD=0xFFF7C450;
    private final Activity host;
    private final MilepostPresentationStore store;
    private final MilepostPresentationStore.State state;
    private MilepostPresentation.Snapshot snapshot;
    private final LinearLayout body,cards;
    private TextView assessmentStatus;
    private TextView revealProgress,revealCipher,revealIdentity,revealBiography,revealThemes;
    private MilepostArtworkView revealArt;
    private android.animation.ValueAnimator animation;
    private int animationTarget;
    private boolean cancellingAnimation;
    static final long ANIMATION_MS=1100;
    private Button revealPrevious,revealNext;
    private TextView revealAvailability;
    private ChapterIndicators revealChapters;
    private final MilepostPresentationReader.PageLoader discoveryPages;
    private int request;
    private MilepostPresentationReader.Page discoveries;
    private boolean viewingDiscoveries,loadingDiscoveries;
    private final SharedPreferences capture;
    private boolean resumed,registered;
    private final SharedPreferences.OnSharedPreferenceChangeListener captureListener=(p,k)->{
        if("active".equals(k)&&p.getBoolean("active",false)){
            // SharedPreferences listeners run on main; latch and persist even if recording stops before render.
            request++;loadingDiscoveries=false;discoveries=null;pauseReplay();render();
        }else if("active".equals(k))render();
    };
    private final Application.ActivityLifecycleCallbacks lifecycle=new Application.ActivityLifecycleCallbacks(){
        public void onActivityResumed(Activity a){if(a==host)onHostResume();}
        public void onActivityPaused(Activity a){if(a==host)onHostPause();}
        public void onActivityDestroyed(Activity a){if(a==host)unregister();}
        public void onActivityCreated(Activity a,Bundle b){}
        public void onActivityStarted(Activity a){}
        public void onActivityStopped(Activity a){}
        public void onActivitySaveInstanceState(Activity a,Bundle b){if(a==host)pauseReplay();}
    };
    MilepostPresentationView(Activity host,MilepostPresentationStore store,MilepostPresentation.Snapshot snapshot,MilepostPresentationReader.PageLoader discoveryPages){
        this(host,store,snapshot,discoveryPages,false);
    }
    MilepostPresentationView(Activity host,MilepostPresentationStore store,MilepostPresentation.Snapshot snapshot,
                            MilepostPresentationReader.PageLoader discoveryPages,boolean resumed){
        super(host);this.discoveryPages=discoveryPages;this.host=host;this.store=store;this.snapshot=snapshot;
        this.resumed=resumed;state=store.read();
        // A process/rotation restart restores the completed static cursor, never an in-flight frame.
        state.paused=true;state.artworkVersion=MilepostPresentation.ARTWORK_VERSION;store.save(state);
        capture=host.getSharedPreferences("roadprints_capture_state",0);
        setFillViewport(true);setBackgroundColor(NAVY);
        body=new LinearLayout(host);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(0,dp(8),0,dp(12));
        cards=new LinearLayout(host);cards.setOrientation(LinearLayout.VERTICAL);buildCards();
        addView(body,new ScrollView.LayoutParams(-1,-2));render();
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();register();if(host.hasWindowFocus())onHostResume();}
    @Override protected void onDetachedFromWindow(){onHostPause();unregister();super.onDetachedFromWindow();}
    private void register(){if(!registered){registered=true;capture.registerOnSharedPreferenceChangeListener(captureListener);host.getApplication().registerActivityLifecycleCallbacks(lifecycle);}}
    private void unregister(){if(registered){registered=false;capture.unregisterOnSharedPreferenceChangeListener(captureListener);host.getApplication().unregisterActivityLifecycleCallbacks(lifecycle);}}
    void onHostResume(){if(resumed)return;resumed=true;if(recording())pauseReplay();render();}
    void onHostPause(){if(!resumed)return;resumed=false;request++;loadingDiscoveries=false;pauseReplay();}
    private boolean recording(){return capture.getBoolean("active",false);}
    java.util.function.BooleanSupplier freshness=()->true;
    private boolean current(){return snapshot.current&&freshness.getAsBoolean();}
    private boolean canReveal(){return resumed&&!recording()&&current()&&snapshot.target()>0&&!host.isFinishing()&&!host.isDestroyed();}
    void update(MilepostPresentation.Snapshot next){
        cancelAnimation();snapshot=next;buildCards();request++;discoveries=null;viewingDiscoveries=false;loadingDiscoveries=false;
        if(!canReveal())pauseReplay();
        // Never reset the cursor when more historical discoveries arrive.
        render();
    }
    private void cancelAnimation(){
        if(animation!=null){cancellingAnimation=true;animation.cancel();animation=null;cancellingAnimation=false;}
        if(revealArt!=null)revealArt.setStage(displayStage());
        if(revealCipher!=null)revealCipher.setText(MilepostPresentation.cipher(displayStage()));
    }
    private void pauseReplay(){cancelAnimation();state.paused=true;store.save(state);}
    void replay(boolean fromBeginning){
        if(!canReveal()){pauseReplay();render();return;}
        cancelAnimation();
        if(fromBeginning)state.cursor=0;
        state.replaying=true;state.paused=false;state.dismissed=false;
        state.artworkVersion=MilepostPresentation.ARTWORK_VERSION;store.save(state);render();
        View control=revealNext!=null&&revealNext.isEnabled()?revealNext:revealPrevious;
        if(control!=null)control.requestFocus(); // ScrollView brings the user-requested controls into view after layout.
    }
    boolean motionAllowed(){return !state.staticMode&&android.animation.ValueAnimator.areAnimatorsEnabled()
        &&!((android.view.accessibility.AccessibilityManager)host.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE)).isTouchExplorationEnabled();}
    void next(){
        if(!canReveal()||state.paused||!state.replaying){pauseReplay();render();return;}
        if(animation!=null||state.cursor>=snapshot.target())return;
        int previous=displayStage(),target=previous+1;
        if(target<=state.completedStage||!motionAllowed()){completeStage(target);return;}
        animationTarget=target;revealNext.setEnabled(false);revealPrevious.setEnabled(false);
        animation=android.animation.ValueAnimator.ofFloat(0,1);animation.setDuration(ANIMATION_MS);
        animation.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animation.addUpdateListener(value->{
            if(!canReveal()){pauseReplay();render();return;}
            float progress=(Float)value.getAnimatedValue();
            revealArt.frame(target,progress);
            revealCipher.setVisibility(View.VISIBLE);
            String cipher=MilepostPresentation.animatedCipher(previous,target,progress);
            revealArt.setCipher(cipher);revealCipher.setText(cipher);
        });
        animation.addListener(new android.animation.AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(android.animation.Animator value){
                if(cancellingAnimation)return;
                animation=null;
                if(canReveal()&&!state.paused)completeStage(animationTarget);else pauseReplay();
            }
        });animation.start();
    }
    private void previous(){
        if(!canReveal()||state.paused||animation!=null||displayStage()<=1)return;
        state.cursor=displayStage()-1;
        store.save(state);
        updateReveal();
        if(!revealPrevious.isEnabled())revealNext.requestFocus();
        revealProgress.announceForAccessibility(chapterLabel(displayStage()));
    }
    private String chapterLabel(int stage){
        return stage==0?"Ready to begin": "Chapter "+stage+" of 7 · "+MilepostPresentation.THEMES[stage];
    }
    private void completeStage(int target){
        state.cursor=target;state.completedStage=Math.max(state.completedStage,target);store.save(state);updateReveal();
        View control=revealNext!=null&&revealNext.isEnabled()?revealNext:revealPrevious;
        if(control!=null)control.requestFocus();
        if(revealProgress!=null)revealProgress.announceForAccessibility(MilepostPresentation.announcement(displayStage()));
    }
    void later(){cancelAnimation();state.paused=true;state.dismissed=true;state.dismissedReceipt=snapshot.receipt;store.save(state);render();}
    void skip(){
        if(!canReveal())return;
        cancelAnimation();state.cursor=snapshot.target();state.completedStage=Math.max(state.completedStage,state.cursor);
        state.replaying=false;state.paused=true;state.dismissed=true;state.dismissedReceipt=snapshot.receipt;
        store.acknowledge(state,snapshot);render();
    }
    int displayStage(){return Math.min(state.cursor,snapshot.target());}
    private void render(){
        cancelAnimation();
        body.removeAllViews();
        revealArt=null;revealPrevious=null;revealNext=null;revealProgress=null;revealCipher=null;
        revealIdentity=null;revealBiography=null;revealThemes=null;revealChapters=null;
        heading(body,"Millennium Mileposts");
        text(body,"Find Mileposts on your recorded walks, runs and rides.",14);
        assessmentStatus=text(body,"",14);
        updateAssessmentStatus();
        if(recording())text(body,"The mystery is paused while you’re recording a journey.",15);
        body.addView(cards,new LinearLayout.LayoutParams(-1,-2));
        if(!recording()&&current()&&snapshot.count>0)
            button(body,"View discoveries","milepost_browse_discoveries",()->loadDiscoveries(0),true);
        if(snapshot.target()>0&&!recording()){
            if(state.replaying&&!state.dismissed&&!state.paused&&current())reveal();
            else button(body,"Open mystery","milepost_replay",()->replay(false),canReveal());
        }
        if(viewingDiscoveries&&!recording())discoveryDetails();
        TextView attribution=text(body,"",12);
        MilepostDetailsDialog.addAttribution(attribution);
    }
    void updateAssessmentStatus(){
        if(assessmentStatus==null)return;
        String message=current()?(snapshot.count==1?"1 Milepost discovered":snapshot.count+" Mileposts discovered")
                :MilepostReconciliationJob.status(host);
        if(!android.text.TextUtils.equals(assessmentStatus.getText(),message))assessmentStatus.setText(message);
    }
    void showAssessmentReadFailure(){
        if(assessmentStatus!=null)assessmentStatus.setText("Couldn’t load your Milepost progress.");
    }
    private void loadDiscoveries(long after){
        if(recording()||!resumed||!current())return;
        viewingDiscoveries=true;loadingDiscoveries=true;int token=++request;String receipt=snapshot.receipt;render();
        discoveryPages.load(after,page->{
            if(token!=request||!receipt.equals(snapshot.receipt)||!resumed||recording())return;
            loadingDiscoveries=false;discoveries=page;
            // Inspection acknowledges this complete summary only after usable details are delivered.
            if(page.current&&current())store.acknowledge(state,snapshot);
            render();View heading=findViewWithTag("milepost_discovery_heading");
            if(heading!=null){heading.setFocusableInTouchMode(true);heading.requestFocus();
                heading.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);}
        });
    }
    private void discoveryDetails(){
        LinearLayout panel=panel(body);TextView title=text(panel,"Discovery record",22);androidx.core.view.ViewCompat.setAccessibilityHeading(title,true);title.setTag("milepost_discovery_heading");
        if(loadingDiscoveries){text(panel,"Loading discoveries…",16);return;}
        if(discoveries==null||!discoveries.current){
            text(panel,"Couldn’t load your discoveries. Please try again.",16);
            button(panel,"Try again","milepost_retry_discoveries",()->loadDiscoveries(0),true);return;
        }
        if(discoveries.discoveries.isEmpty())text(panel,"No Mileposts discovered yet.",16);
        for(MilepostPresentationReader.Discovery d:discoveries.discoveries){
            MilepostArtworkView designIcon=MilepostArtworkView.icon(host,MilepostArtworkView.DESIGNS[d.design]);panel.addView(designIcon,new LinearLayout.LayoutParams(dp(48),dp(48)));
            text(panel,d.designLabel,16);
            text(panel,d.date==null?"Discovery date unavailable":MilepostDetailsDialog.displayDate(d.date),14);
        }
        if(discoveries.more){long after=discoveries.discoveries.get(discoveries.discoveries.size()-1).ordinal;
            button(panel,"Next discoveries","milepost_next_discoveries",()->loadDiscoveries(after),true);}
        button(panel,"First discoveries","milepost_first_discoveries",()->loadDiscoveries(0),true);
    }
    private void buildCards(){
        cards.removeAllViews();
        int hidden=0;
        for(int index=0;index<5;index++){
            MilepostPresentation.Card achievement=snapshot.card(index);
            if(achievement.earned)card(index,achievement,achievement.biography);
            else hidden++;
        }
        if(hidden>0){
            String title=hidden==1?"1 hidden personality":hidden+" hidden personalities";
            achievementCard(false,"LOCKED",R.drawable.milepost_mystery,title,"",
                    "Discover more Mileposts and explore the National Cycle Network.").setTag("milepost_hidden_personalities");
        }
        MilepostPresentation.Card mystery=snapshot.card(5);
        String chapters=mystery.stage==0?"Discover a Milepost to unlock the first chapter."
                :mystery.stage+" of 7 chapters unlocked";
        card(5,mystery,mystery.earned?mystery.biography:chapters);
        MilepostPresentation.Card collective=snapshot.card(6);
        card(6,collective,snapshot.individuals()+" of 6 personalities revealed");
    }
    private void card(int index,MilepostPresentation.Card achievement,String detail){
        int icon=achievement.earned?(index==6?R.drawable.milepost_collective:MilepostArtworkView.MEDALLIONS[index])
                :R.drawable.milepost_mystery;
        String note=achievement.earned?"":achievement.status;
        String progress=index==5&&achievement.earned?"7 of 7 chapters unlocked":note;
        String status=achievement.earned?"UNLOCKED":achievement.stage>0?"IN PROGRESS":"LOCKED";
        achievementCard(achievement.earned,status,icon,achievement.title,progress,detail).setTag("milepost_card_"+index);
    }
    private LinearLayout achievementCard(boolean unlocked,String stateLabel,int artwork,String title,String progress,String detail){
        LinearLayout panel=AchievementCardStyle.milepostCard(host,unlocked);
        cards.addView(panel);
        LinearLayout row=new LinearLayout(host);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        panel.addView(row);
        MilepostArtworkView icon=MilepostArtworkView.icon(host,artwork);
        AchievementCardStyle.badge(icon,unlocked);
        row.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout copy=new LinearLayout(host);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12),0,0,0);
        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        TextView status=cardText(copy,stateLabel,10);
        status.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        status.setTextColor(unlocked?GOLD:0xFF8DE6DE);
        cardText(copy,title,16).setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        if(!progress.isEmpty())cardText(copy,progress,13).setTextColor(MUTED);
        if(!detail.isEmpty())cardText(copy,detail,13).setTextColor(MUTED);
        return panel;
    }
    private TextView cardText(LinearLayout parent,String value,int size){
        TextView text=text(parent,value,size);
        text.setPadding(0,dp(2),0,dp(2));
        return text;
    }
    private void reveal(){
        LinearLayout panel=panel(body);
        LinearLayout header=new LinearLayout(host);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        panel.addView(header);
        TextView title=text(header,"Mystery reveal",21);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        androidx.core.view.ViewCompat.setAccessibilityHeading(title,true);
        Button close=quietButton(header,"Close","milepost_reveal_later",this::later);
        close.setContentDescription("Close mystery");
        close.setLayoutParams(new LinearLayout.LayoutParams(-2,-2));
        revealArt=new MilepostArtworkView(host,displayStage());revealArt.enableCipherRing();revealArt.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams artParams=new LinearLayout.LayoutParams(-1,dp(190));
        artParams.topMargin=dp(4);
        artParams.bottomMargin=dp(8);
        panel.addView(revealArt,artParams);
        revealChapters=new ChapterIndicators(host);
        revealChapters.setTag("milepost_chapter_indicators");
        panel.addView(revealChapters,new LinearLayout.LayoutParams(-1,dp(24)));
        revealProgress=text(panel,"",18);revealProgress.setTag("milepost_reveal_progress");
        revealThemes=text(panel,"",16);revealCipher=text(panel,"",20);revealCipher.setTag("milepost_replay_cipher");
        revealCipher.setGravity(android.view.Gravity.CENTER);revealCipher.setTypeface(Typeface.MONOSPACE);revealCipher.setLetterSpacing(0.08f);
        revealCipher.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);revealCipher.setFocusable(false);
        revealIdentity=text(panel,"",22);revealBiography=text(panel,"",16);
        revealAvailability=text(panel,"",14);
        LinearLayout navigation=new LinearLayout(host);
        panel.addView(navigation,new LinearLayout.LayoutParams(-1,-2));
        revealPrevious=button(navigation,"Previous","milepost_previous",this::previous,false);
        revealNext=button(navigation,"Next","milepost_next",this::next,false);
        revealPrevious.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams nextParams=new LinearLayout.LayoutParams(0,-2,1);
        nextParams.setMarginStart(dp(8));revealNext.setLayoutParams(nextParams);
        updateReveal();
        Button motion=quietButton(panel,state.staticMode?"Animation off":"Animation on","milepost_motion",()->{
            cancelAnimation();state.staticMode=!state.staticMode;store.save(state);render();
        });
        motion.setContentDescription(state.staticMode?"Turn animation on":"Turn animation off");
        LinearLayout.LayoutParams motionParams=new LinearLayout.LayoutParams(-2,-2);
        motionParams.gravity=android.view.Gravity.END;
        motion.setLayoutParams(motionParams);
    }
    /** Next changes only the current composition and native text, retaining the six cards and controls. */
    private void updateReveal(){
        if(revealProgress==null)return;
        int stage=displayStage();revealArt.setStage(stage);
        revealProgress.setText(chapterLabel(stage));
        revealChapters.show(stage,snapshot.target());
        revealCipher.setText(MilepostPresentation.cipher(stage));revealCipher.setVisibility(stage==0?View.GONE:View.VISIBLE);
        revealThemes.setText(stage>0&&stage<7?themeLabels(stage):"");revealThemes.setVisibility(stage>0&&stage<7?View.VISIBLE:View.GONE);
        revealIdentity.setText(stage==7?snapshot.card(5).title:"");revealBiography.setText(stage==7?snapshot.card(5).biography:"");
        revealIdentity.setVisibility(stage==7?View.VISIBLE:View.GONE);revealBiography.setVisibility(stage==7?View.VISIBLE:View.GONE);
        revealPrevious.setEnabled(canReveal()&&!state.paused&&animation==null&&stage>1);
        revealNext.setEnabled(canReveal()&&!state.paused&&animation==null&&stage<snapshot.target());
        boolean locked=stage==snapshot.target()&&stage<7;
        revealAvailability.setVisibility(locked?View.VISIBLE:View.GONE);
        revealAvailability.setText(locked?"Find another Milepost to unlock the next chapter.":"");
    }
    private String themeLabels(int stage){return android.text.TextUtils.join(" · ",MilepostPresentation.layers(stage).stream()
        .map(s->s.substring(16).replace('-',' ')).toArray());}
    private LinearLayout column(LinearLayout parent){LinearLayout v=new LinearLayout(host);v.setOrientation(LinearLayout.VERTICAL);parent.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;}
    private LinearLayout panel(LinearLayout parent){
        LinearLayout v=column(parent);v.setPadding(dp(15),dp(14),dp(15),dp(14));
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xFF172F65);bg.setCornerRadius(dp(16));bg.setStroke(dp(1),0xFF46649E);v.setBackground(bg);
        LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)v.getLayoutParams();p.bottomMargin=dp(12);v.setLayoutParams(p);return v;
    }
    private TextView text(LinearLayout parent,String value,float size){
        TextView t=new TextView(host);t.setText(value);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(0,dp(6),0,dp(6));
        parent.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;
    }
    private void heading(LinearLayout parent,String value){TextView t=text(parent,value,22);t.setTypeface(null,Typeface.BOLD);androidx.core.view.ViewCompat.setAccessibilityHeading(t,true);}
    private Button button(LinearLayout parent,String label,String tag,Runnable action,boolean enabled){
        Button b=new Button(host);b.setText(label);b.setTextSize(14);b.setAllCaps(false);b.setMinHeight(dp(48));b.setTextColor(new android.content.res.ColorStateList(
            new int[][]{new int[]{-android.R.attr.state_enabled},new int[0]},new int[]{MUTED,0xFF8DE6DE}));
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF233B78));b.setTag(tag);b.setEnabled(enabled);
        b.setOnClickListener(v->action.run());parent.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;
    }
    private Button quietButton(LinearLayout parent,String label,String tag,Runnable action){
        Button button=button(parent,label,tag,action,true);
        android.util.TypedValue ripple=new android.util.TypedValue();
        host.getTheme().resolveAttribute(android.R.attr.selectableItemBackground,ripple,true);
        button.setBackgroundResource(ripple.resourceId);
        button.setBackgroundTintList(null);
        button.setTextColor(MUTED);
        return button;
    }
    private final class ChapterIndicators extends View {
        private static final int TOTAL=7;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private int current,unlocked;

        ChapterIndicators(Activity host){super(host);}

        void show(int current,int unlocked){
            this.current=current;
            this.unlocked=unlocked;
            setContentDescription(unlocked+" of "+TOTAL+" chapters unlocked"
                    +(current==0?"":". Viewing chapter "+current));
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas){
            float step=Math.min(dp(22),getWidth()/(float)TOTAL);
            float x=getWidth()/2f-3*step,y=getHeight()/2f;
            for(int chapter=1;chapter<=TOTAL;chapter++,x+=step){
                boolean selected=chapter==current;
                paint.setStyle(chapter>unlocked||selected?Paint.Style.STROKE:Paint.Style.FILL);
                paint.setStrokeWidth(dp(selected?2:1));
                paint.setColor(selected?GOLD:chapter<=unlocked?0xFF8DE6DE:0xFF8299BF);
                canvas.drawCircle(x,y,dp(selected?6:4),paint);
                if(selected){
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(x,y,dp(2),paint);
                }
            }
        }
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
