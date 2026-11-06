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

/** Inactive, embeddable native experience. No production caller, manifest entry or award writer.
 * Accepts only a worker-prepared compact receipt. Lifecycle and capture suspend optional replay.
 * Static, explicit transitions are also the reduced-motion alternative; no timed task is needed. */
final class MilepostPresentationView extends ScrollView {
    private static final int NAVY=0xFF0B1C50,CARD=0xFF233B78,MUTED=0xFFB9C5D8,GOLD=0xFFF7C450;
    private final Activity host;
    private final MilepostPresentationStore store;
    private final MilepostPresentationStore.State state;
    private MilepostPresentation.Snapshot snapshot;
    private final LinearLayout body;
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
        addView(body,new ScrollView.LayoutParams(-1,-2));render();
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();register();if(host.hasWindowFocus())onHostResume();}
    @Override protected void onDetachedFromWindow(){onHostPause();unregister();super.onDetachedFromWindow();}
    private void register(){if(!registered){registered=true;capture.registerOnSharedPreferenceChangeListener(captureListener);host.getApplication().registerActivityLifecycleCallbacks(lifecycle);}}
    private void unregister(){if(registered){registered=false;capture.unregisterOnSharedPreferenceChangeListener(captureListener);host.getApplication().unregisterActivityLifecycleCallbacks(lifecycle);}}
    void onHostResume(){resumed=true;if(recording())pauseReplay();render();}
    void onHostPause(){resumed=false;request++;loadingDiscoveries=false;pauseReplay();}
    private boolean recording(){return capture.getBoolean("active",false);}
    private boolean canReveal(){return resumed&&!recording()&&snapshot.current&&snapshot.introduced()&&!host.isFinishing()&&!host.isDestroyed();}
    void update(MilepostPresentation.Snapshot next){
        snapshot=next;request++;discoveries=null;viewingDiscoveries=false;loadingDiscoveries=false;
        if(!canReveal())pauseReplay();
        // Never reset the cursor when more historical discoveries arrive.
        render();
    }
    private void pauseReplay(){state.paused=true;store.save(state);}
    void replay(boolean fromBeginning){
        if(!canReveal()){pauseReplay();render();return;}
        if(fromBeginning)state.cursor=0;
        state.replaying=true;state.paused=false;state.dismissed=false;
        state.artworkVersion=MilepostPresentation.ARTWORK_VERSION;store.save(state);render();
    }
    void next(){
        if(!canReveal()||state.paused||!state.replaying){pauseReplay();render();return;}
        if(state.cursor>=snapshot.target())return;
        state.cursor++;
        state.completedStage=Math.max(state.completedStage,state.cursor);
        store.save(state);render();
        View control=findViewWithTag("milepost_next");
        if(control==null||!control.isEnabled())control=findViewWithTag("milepost_skip");
        if(control!=null)control.requestFocus();
        View stage=findViewWithTag("milepost_reveal_progress");if(stage!=null)stage.announceForAccessibility(MilepostPresentation.announcement(displayStage()));
    }
    void later(){state.paused=true;state.dismissed=true;state.dismissedReceipt=snapshot.receipt;store.save(state);render();}
    void skip(){
        if(!canReveal())return;
        state.cursor=snapshot.target();state.completedStage=Math.max(state.completedStage,state.cursor);
        state.replaying=false;state.paused=true;state.dismissed=true;state.dismissedReceipt=snapshot.receipt;
        store.acknowledge(state,snapshot);render();
    }
    int displayStage(){return Math.min(state.cursor,snapshot.target());}
    private void render(){
        body.removeAllViews();
        heading(body,"Millennium Mileposts");
        text(body,snapshot.current?"Discoveries: "+snapshot.count:"Updating assessment. Showing the previous complete result.",16);
        if(recording()){
            text(body,"Reveal paused while recording. Your journey has priority.",16);
            // No summary actions or reveal art while capture is active.
        }else if(store.pending(state,snapshot)&&(!state.dismissed||!snapshot.receipt.equals(state.dismissedReceipt)))summary();
        LinearLayout cards=column(body);
        for(int i=0;i<6;i++)card(cards,i);
        if(!recording()&&snapshot.current&&(snapshot.count>0||snapshot.incomplete>0))
            button(body,"View discoveries","milepost_browse_discoveries",()->loadDiscoveries(0),true);
        if(snapshot.introduced()){
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
            if(!snapshot.current||recording())return;
            state.cursor=snapshot.target();state.completedStage=Math.max(state.completedStage,state.cursor);
            state.replaying=false;state.paused=true;state.dismissed=true;state.dismissedReceipt=snapshot.receipt;
            store.acknowledge(state,snapshot);render();
        },true);
    }
    private void loadDiscoveries(long after){
        if(recording()||!resumed)return;
        viewingDiscoveries=true;loadingDiscoveries=true;int token=++request;String receipt=snapshot.receipt;render();
        discoveryPages.load(after,page->{
            if(token!=request||!receipt.equals(snapshot.receipt)||!resumed||recording())return;
            loadingDiscoveries=false;discoveries=page;
            // Inspection acknowledges this complete summary only after usable details are delivered.
            if(page.current)store.acknowledge(state,snapshot);
            render();View heading=findViewWithTag("milepost_discovery_heading");
            if(heading!=null)heading.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);
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
        for(MilepostPresentationReader.Discovery d:discoveries.discoveries)
            text(panel,"Discovery "+d.ordinal+" · "+d.marker+" · "+(d.date==null?"Historical date unavailable":d.date+" (journey date)"),16);
        if(discoveries.more){long after=discoveries.discoveries.get(discoveries.discoveries.size()-1).ordinal;
            button(panel,"Next discoveries","milepost_next_discoveries",()->loadDiscoveries(after),true);}
        button(panel,"First discoveries","milepost_first_discoveries",()->loadDiscoveries(0),true);
    }
    private void card(LinearLayout parent,int index){
        MilepostPresentation.Card c=snapshot.card(index);LinearLayout panel=panel(parent);
        // Native text and a neutral generic icon. No identity appears in tags, descriptions or navigation for locked cards.
        PlaceholderArt icon=new PlaceholderArt(host,snapshot.introduced()?7:0);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);panel.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));
        text(panel,c.title,20);text(panel,c.status,16);
        if(!c.biography.isEmpty())text(panel,c.biography,16);
        if(index==5&&c.stage>0&&!recording()){
            art(panel,c.stage);text(panel,MilepostPresentation.announcement(c.stage),16);cipher(panel,c.stage);
        }
        panel.setTag("milepost_card_"+index); // Anonymous stable position only.
    }
    private void reveal(){
        int stage=displayStage();LinearLayout panel=panel(body);heading(panel,"Mystery reveal");
        art(panel,stage);TextView progress=text(panel,MilepostPresentation.announcement(stage),18);progress.setTag("milepost_reveal_progress");
        cipher(panel,stage);
        if(stage==7){text(panel,snapshot.card(5).title,22);text(panel,snapshot.card(5).biography,16);}
        if(state.paused)text(panel,"Paused. Resume when ready.",16);
        button(panel,"Next","milepost_next",this::next,canReveal()&&!state.paused&&stage<snapshot.target());
        button(panel,"Skip","milepost_skip",this::skip,canReveal());
        button(panel,"Later","milepost_reveal_later",this::later,true);
        button(panel,"Replay","milepost_replay_again",()->replay(true),canReveal());
        text(panel,"Static reveal. Each completed stage stays available without motion, sound or timing.",16);
    }
    private void cipher(LinearLayout parent,int stage){
        if(stage==0)return;
        TextView cipher=text(parent,MilepostPresentation.cipher(stage),20);
        cipher.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        cipher.setFocusable(false);cipher.setTag("milepost_cipher");
    }
    private void art(LinearLayout parent,int stage){
        PlaceholderArt art=new PlaceholderArt(host,stage);art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        parent.addView(art,new LinearLayout.LayoutParams(-1,dp(180)));
        if(stage>0&&stage<7)text(parent,android.text.TextUtils.join(" · ",MilepostPresentation.layers(stage).stream()
            .map(s->s.substring(16).replace('-',' ')).toArray()),16);
    }
    /** Replaceable six independently reconstructible panels, opaque identity veil, separate generic silhouette.
     * No portrait or historical likeness is claimed. Only the current composition exists; zero bitmap decoding. */
    static final class PlaceholderArt extends View {
        final int stage;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        PlaceholderArt(Activity context,int stage){super(context);this.stage=MilepostPresentation.stage(stage);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float w=getWidth(),h=getHeight();
            paint.setColor(MUTED);canvas.drawCircle(w/2,h*.35f,h*.12f,paint);
            canvas.drawOval(w/2-h*.22f,h*.50f,w/2+h*.22f,h*.85f,paint);
            if(stage<7){
                paint.setColor(NAVY);canvas.drawRect(w/2-h*.25f,h*.15f,w/2+h*.25f,h*.9f,paint);
                for(int i=0;i<stage;i++){
                    // Fixed reserved panels do not depend on a prior frame or on other layers.
                    float x=(i%3)*w/3,y=(i/3)*h/2;
                    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);paint.setColor(GOLD);
                    canvas.drawRect(x+4,y+4,x+w/3-4,y+h/2-4,paint);paint.setStyle(Paint.Style.FILL);
                }
                paint.setColor(Color.WHITE);paint.setTextSize(h*.20f);paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("?",w/2,h*.6f,paint);
            }
        }
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
    private void button(LinearLayout parent,String label,String tag,Runnable action,boolean enabled){
        Button b=new Button(host);b.setText(label);b.setTextSize(16);b.setAllCaps(false);b.setMinHeight(dp(48));b.setTextColor(NAVY);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GOLD));b.setTag(tag);b.setEnabled(enabled);
        b.setOnClickListener(v->action.run());parent.addView(b,new LinearLayout.LayoutParams(-1,-2));
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
