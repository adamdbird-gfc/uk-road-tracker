package com.roadprints.capture;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.util.List;

/** Embeddable production experience. Entitlements are read-only; replay writes presentation only.
 * Accepts only a worker-prepared compact receipt. Lifecycle and capture suspend optional replay.
 * Static, explicit transitions are also the reduced-motion alternative; no timed task is needed. */
final class MilepostPresentationView extends ScrollView {
    private static final int NAVY=0xFF0B1C50,CARD=0xFF242832,MUTED=0xFFB9C5D8,GOLD=0xFFF7C450;
    private final Activity host;
    private final MilepostPresentationStore store;
    private final MilepostPresentationStore.State state;
    private MilepostPresentation.Snapshot snapshot;
    private final LinearLayout body,cards;
    private LinearLayout cardProgress;
    private TextView revealProgress,revealCipher,revealIdentity,revealBiography,revealThemes;
    private MilepostArtworkView revealArt;
    private android.animation.ValueAnimator animation;
    private int animationTarget;
    private boolean cancellingAnimation;
    static final long ANIMATION_MS=1100;
    private Button revealNext;
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
        super(host);this.discoveryPages=discoveryPages;this.host=host;this.store=store;this.snapshot=snapshot;state=store.read();
        // A process/rotation restart restores the completed static cursor, never an in-flight frame.
        state.paused=true;state.artworkVersion=MilepostPresentation.ARTWORK_VERSION;store.save(state);
        capture=host.getSharedPreferences("roadprints_capture_state",0);
        setFillViewport(true);setBackgroundColor(NAVY);
        body=new LinearLayout(host);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(12),dp(18),dp(18));
        cards=new LinearLayout(host);cards.setOrientation(LinearLayout.VERTICAL);buildCards();
        addView(body,new ScrollView.LayoutParams(-1,-2));render();
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();register();if(host.hasWindowFocus())onHostResume();}
    @Override protected void onDetachedFromWindow(){onHostPause();unregister();super.onDetachedFromWindow();}
    private void register(){if(!registered){registered=true;capture.registerOnSharedPreferenceChangeListener(captureListener);host.getApplication().registerActivityLifecycleCallbacks(lifecycle);}}
    private void unregister(){if(registered){registered=false;capture.unregisterOnSharedPreferenceChangeListener(captureListener);host.getApplication().unregisterActivityLifecycleCallbacks(lifecycle);}}
    void onHostResume(){resumed=true;if(recording())pauseReplay();render();}
    void onHostPause(){resumed=false;request++;loadingDiscoveries=false;pauseReplay();}
    private boolean recording(){return capture.getBoolean("active",false);}
    java.util.function.BooleanSupplier freshness=()->true;
    private boolean current(){return snapshot.current&&freshness.getAsBoolean();}
    private boolean canReveal(){return resumed&&!recording()&&current()&&snapshot.introduced()&&!host.isFinishing()&&!host.isDestroyed();}
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
        View control=revealNext!=null&&revealNext.isEnabled()?revealNext:findViewWithTag("milepost_skip");
        if(control!=null)control.requestFocus(); // ScrollView brings the user-requested controls into view after layout.
    }
    boolean motionAllowed(){return !state.staticMode&&android.animation.ValueAnimator.areAnimatorsEnabled()
        &&!((android.view.accessibility.AccessibilityManager)host.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE)).isTouchExplorationEnabled();}
    void next(){
        if(!canReveal()||state.paused||!state.replaying){pauseReplay();render();return;}
        if(animation!=null||state.cursor>=snapshot.target())return;
        int previous=displayStage(),target=previous+1;
        if(!motionAllowed()){completeStage(target);return;}
        animationTarget=target;revealNext.setEnabled(false);
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
    private void completeStage(int target){
        state.cursor=target;state.completedStage=Math.max(state.completedStage,target);store.save(state);updateReveal();
        View control=revealNext!=null&&revealNext.isEnabled()?revealNext:findViewWithTag("milepost_skip");
        if(control!=null)control.requestFocus();
        if(revealProgress!=null)revealProgress.announceForAccessibility(MilepostPresentation.announcement(displayStage()));
    }
    void later(){cancelAnimation();state.paused=true;state.dismissed=true;state.dismissedReceipt=snapshot.receipt;store.save(state);render();}
    void skip(){
        if(!canReveal())return;
        cancelAnimation();cancelAnimation();state.cursor=snapshot.target();state.completedStage=Math.max(state.completedStage,state.cursor);
        state.replaying=false;state.paused=true;state.dismissed=true;state.dismissedReceipt=snapshot.receipt;
        store.acknowledge(state,snapshot);render();
    }
    int displayStage(){return Math.min(state.cursor,snapshot.target());}
    private void render(){
        cancelAnimation();
        body.removeAllViews();
        heading(body,"Millennium Mileposts");
        TextView attribution=text(body,"© OpenStreetMap contributors · ODbL 1.0\nhttps://www.openstreetmap.org/copyright",12);
        android.text.util.Linkify.addLinks(attribution,android.text.util.Linkify.WEB_URLS);
        attribution.setLinkTextColor(GOLD);
        text(body,"Explore 887 approved physical mileposts on foot or by bicycle. Pass within 100 metres with verified journey evidence to discover a post.",16);
        text(body,snapshot.current?"Discoveries: "+snapshot.count+" of 887":
            "Historical assessment pending. Discoveries have not yet been verified. Assessment continues in small background steps when the device is charging and idle.",16);
        if(recording()){
            text(body,"Reveal paused while recording. Your journey has priority.",16);
            // No summary actions or reveal art while capture is active.
        }else if(current()&&store.pending(state,snapshot)&&(!state.dismissed||!snapshot.receipt.equals(state.dismissedReceipt)))summary();
        body.addView(cards,new LinearLayout.LayoutParams(-1,-2));
        if(cardProgress!=null)cardProgress.setVisibility(recording()?View.GONE:View.VISIBLE);
        if(!recording()&&current()&&(snapshot.count>0||snapshot.incomplete>0))
            button(body,"View discoveries","milepost_browse_discoveries",()->loadDiscoveries(0),true);
        if(snapshot.introduced()){
            MilepostArtworkView collective=MilepostArtworkView.icon(host,R.drawable.milepost_collective);
            body.addView(collective,new LinearLayout.LayoutParams(dp(48),dp(48)));
            heading(body,"In Honour of Time");
            text(body,snapshot.individuals()+" of 6 personalities"+(snapshot.awards.get(6).earned?". Collective achievement earned.":"."),16);
            if(!recording()){
                button(body,"Replay reveal","milepost_replay",()->replay(true),canReveal());
                if(state.replaying||state.cursor>0)button(body,"Resume reveal","milepost_resume",()->replay(false),canReveal());
            }
        }
        if(state.replaying&&!state.dismissed&&!recording()&&snapshot.introduced())reveal();
        if(viewingDiscoveries&&!recording())discoveryDetails();
    }
    private void summary(){
        LinearLayout panel=panel(body);heading(panel,"Discovery summary");
        text(panel,snapshot.newPosts+" newly discovered unique physical posts.",16);
        List<String> earned=snapshot.newAwards(state.acknowledgedAwards);
        if(!earned.isEmpty())text(panel,"Achievements earned: "+android.text.TextUtils.join(", ",earned),16);
        text(panel,snapshot.dates(),16);
        boolean unknown=false;for(MilepostPresentation.Award a:snapshot.awards)if(a.earned&&a.ordinal>state.acknowledgedAwards&&a.date==null)unknown=true;
        if(unknown)text(panel,"Some achievement dates are unknown. Recognition time is not a historical event date.",16);
        text(panel,snapshot.pending+" journeys awaiting assessment; "+snapshot.incomplete+" with incomplete evidence or catalogue checks.",16);
        button(panel,"View discoveries","milepost_view_discoveries",()->loadDiscoveries(0),true);
        button(panel,"Replay reveal","milepost_summary_replay",()->replay(true),canReveal());
        button(panel,"Later","milepost_later",this::later,true);
        button(panel,"Skip celebrations","milepost_skip_summary",()->{
            if(!current()||recording())return;
            cancelAnimation();state.cursor=snapshot.target();state.completedStage=Math.max(state.completedStage,state.cursor);
            state.replaying=false;state.paused=true;state.dismissed=true;state.dismissedReceipt=snapshot.receipt;
            store.acknowledge(state,snapshot);render();
        },true);
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
            text(panel,"Discovery details need a current assessment. Try again when processing is complete.",16);
            button(panel,"Try again","milepost_retry_discoveries",()->loadDiscoveries(0),true);return;
        }
        if(discoveries.discoveries.isEmpty())text(panel,"No currently qualifying physical discoveries.",16);
        for(MilepostPresentationReader.Discovery d:discoveries.discoveries){
            MilepostArtworkView designIcon=MilepostArtworkView.icon(host,MilepostArtworkView.DESIGNS[d.design]);panel.addView(designIcon,new LinearLayout.LayoutParams(dp(48),dp(48)));
            text(panel,"Discovery "+d.ordinal+" · "+d.marker+" · "+(d.date==null?"Historical date unavailable":d.date+" (journey date)"),16);
            text(panel,d.designLabel+" · OSM source location",14);
        }
        if(discoveries.more){long after=discoveries.discoveries.get(discoveries.discoveries.size()-1).ordinal;
            button(panel,"Next discoveries","milepost_next_discoveries",()->loadDiscoveries(after),true);}
        button(panel,"First discoveries","milepost_first_discoveries",()->loadDiscoveries(0),true);
    }
    private void buildCards(){
        cards.removeAllViews();cardProgress=null;
        for(int i=0;i<6;i++)card(cards,i);
        if(!snapshot.introduced()){
            card(cards,6);
            LinearLayout locked=panel(cards);heading(locked,"Mystery reveal");
            art(locked,0);text(locked,"Locked seven-stage cipher. Discover mileposts to reveal the mystery.",16);
        }
    }
    private void card(LinearLayout parent,int index){
        MilepostPresentation.Card c=snapshot.card(index);LinearLayout panel=panel(parent);
        // Native text and a neutral generic icon. No identity appears in tags, descriptions or navigation for locked cards.
        MilepostArtworkView icon=MilepostArtworkView.icon(host,c.earned?(index==6?R.drawable.milepost_collective:MilepostArtworkView.MEDALLIONS[index]):R.drawable.milepost_mystery);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);panel.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));
        text(panel,c.title,20);text(panel,c.status,16);
        if(!c.biography.isEmpty())text(panel,c.biography,16);
        if(index==5&&c.stage>0){
            cardProgress=column(panel);art(cardProgress,c.stage);text(cardProgress,MilepostPresentation.announcement(c.stage),16);cipher(cardProgress,c.stage);
        }
        panel.setTag("milepost_card_"+index); // Anonymous stable position only.
    }
    private void reveal(){
        LinearLayout panel=panel(body);heading(panel,"Mystery reveal");
        revealArt=new MilepostArtworkView(host,displayStage());revealArt.enableCipherRing();revealArt.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        panel.addView(revealArt,new LinearLayout.LayoutParams(-1,dp(180)));
        revealProgress=text(panel,"",18);revealProgress.setTag("milepost_reveal_progress");
        revealThemes=text(panel,"",16);revealCipher=text(panel,"",20);revealCipher.setTag("milepost_replay_cipher");
        revealCipher.setGravity(android.view.Gravity.CENTER);revealCipher.setTypeface(Typeface.MONOSPACE);revealCipher.setLetterSpacing(0.08f);
        revealCipher.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);revealCipher.setFocusable(false);
        revealIdentity=text(panel,"",22);revealBiography=text(panel,"",16);
        if(state.paused)text(panel,"Paused. Resume when ready.",16);
        revealNext=button(panel,"Next","milepost_next",this::next,false);
        updateReveal();
        button(panel,"Skip","milepost_skip",this::skip,canReveal());
        button(panel,"Later","milepost_reveal_later",this::later,true);
        button(panel,"Replay","milepost_replay_again",()->replay(true),canReveal());
        button(panel,state.staticMode?"Enable animation":"Use static reveal","milepost_motion",()->{
            pauseReplay();state.staticMode=!state.staticMode;store.save(state);render();
        },true);
        text(panel,"Each completed stage stays available. Static reveal follows your motion and accessibility settings.",16);
    }
    /** Next changes only the current composition and native text, retaining the six cards and controls. */
    private void updateReveal(){
        if(revealProgress==null)return;
        int stage=displayStage();revealArt.setStage(stage);
        revealProgress.setText(MilepostPresentation.announcement(stage));
        revealCipher.setText(MilepostPresentation.cipher(stage));revealCipher.setVisibility(stage==0?View.GONE:View.VISIBLE);
        revealThemes.setText(stage>0&&stage<7?themeLabels(stage):"");revealThemes.setVisibility(stage>0&&stage<7?View.VISIBLE:View.GONE);
        revealIdentity.setText(stage==7?snapshot.card(5).title:"");revealBiography.setText(stage==7?snapshot.card(5).biography:"");
        revealIdentity.setVisibility(stage==7?View.VISIBLE:View.GONE);revealBiography.setVisibility(stage==7?View.VISIBLE:View.GONE);
        revealNext.setEnabled(canReveal()&&!state.paused&&stage<snapshot.target());
    }
    private String themeLabels(int stage){return android.text.TextUtils.join(" · ",MilepostPresentation.layers(stage).stream()
        .map(s->s.substring(16).replace('-',' ')).toArray());}
    private void cipher(LinearLayout parent,int stage){
        if(stage==0)return;
        TextView cipher=text(parent,MilepostPresentation.cipher(stage),20);
        cipher.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        cipher.setFocusable(false);cipher.setTag("milepost_cipher");
    }
    private void art(LinearLayout parent,int stage){
        MilepostArtworkView art=new MilepostArtworkView(host,stage);art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        parent.addView(art,new LinearLayout.LayoutParams(-1,dp(180)));
        if(stage>0&&stage<7)text(parent,themeLabels(stage),16);
    }
    private LinearLayout column(LinearLayout parent){LinearLayout v=new LinearLayout(host);v.setOrientation(LinearLayout.VERTICAL);parent.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;}
    private LinearLayout panel(LinearLayout parent){
        LinearLayout v=column(parent);v.setPadding(dp(15),dp(14),dp(15),dp(14));
        GradientDrawable bg=new GradientDrawable();bg.setColor(CARD);bg.setCornerRadius(dp(16));bg.setStroke(dp(1),0xFF7189B8);v.setBackground(bg);
        LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)v.getLayoutParams();p.bottomMargin=dp(12);v.setLayoutParams(p);return v;
    }
    private TextView text(LinearLayout parent,String value,float size){
        TextView t=new TextView(host);t.setText(value);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(0,dp(6),0,dp(6));
        parent.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;
    }
    private void heading(LinearLayout parent,String value){TextView t=text(parent,value,22);t.setTypeface(null,Typeface.BOLD);androidx.core.view.ViewCompat.setAccessibilityHeading(t,true);}
    private Button button(LinearLayout parent,String label,String tag,Runnable action,boolean enabled){
        Button b=new Button(host);b.setText(label);b.setTextSize(16);b.setAllCaps(false);b.setMinHeight(dp(48));b.setTextColor(NAVY);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GOLD));b.setTag(tag);b.setEnabled(enabled);
        b.setOnClickListener(v->action.run());parent.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
