package com.roadprints.capture;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Original line-vector compositions. Only the current drawable is retained; no bitmaps or portraits. */
final class MilepostArtworkView extends android.widget.FrameLayout {
    static final int[] MEDALLIONS={R.drawable.milepost_medallion_mills,R.drawable.milepost_medallion_rowe,
        R.drawable.milepost_medallion_mccoll,R.drawable.milepost_medallion_dudgeon,
        R.drawable.milepost_medallion_grimshaw,R.drawable.milepost_medallion_harrow};
    static final int[] THEMES={R.drawable.milepost_mystery,R.drawable.milepost_theme_seasons,
        R.drawable.milepost_theme_continents,R.drawable.milepost_theme_ages,R.drawable.milepost_theme_zodiac,
        R.drawable.milepost_theme_centuries,R.drawable.milepost_theme_mystery,R.drawable.milepost_theme_harrow};
    static final int[] DESIGNS={R.drawable.milepost_design_mills,R.drawable.milepost_design_rowe,
        R.drawable.milepost_design_mccoll,R.drawable.milepost_design_dudgeon,R.drawable.milepost_design_unknown};
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Drawable inner,outer,layer;
    private Drawable emblem;
    private int stage,resource,icon;
    private float fraction=1;
    private boolean transitioning,ringText;
    private android.widget.TextView[] glyphs;
    private String cipher="";
    MilepostArtworkView(Context context,int stage){this(context,stage,false);}
    private MilepostArtworkView(Context context,int value,boolean isIcon){
        super(context);setWillNotDraw(false);
        if(isIcon)setIcon(value);else setStage(value);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    static MilepostArtworkView icon(Context context,int resource){return new MilepostArtworkView(context,resource,true);}
    void enableCipherRing(){ringText=true;setCipher(MilepostPresentation.cipher(stage));}
    private Drawable drawable(int id){return getResources().getDrawable(id,getContext().getTheme()).mutate();}
    void setIcon(int id){icon=id;use(id);transitioning=false;setCipher("");invalidate();}
    void setStage(int value){stage=MilepostPresentation.stage(value);icon=0;transitioning=false;fraction=1;use(THEMES[stage]);setCipher(MilepostPresentation.cipher(stage));invalidate();}
    private void use(int id){if(id!=resource){resource=id;emblem=drawable(id);}}
    void frame(int target,float value){stage=MilepostPresentation.stage(target);icon=0;transitioning=true;fraction=value;use(THEMES[stage]);invalidate();}
    void setCipher(String text){
        cipher=ringText?text.replace(" ",""):"";
        if(!cipher.isEmpty()&&glyphs==null){
            glyphs=new android.widget.TextView[13];
            for(int i=0;i<glyphs.length;i++){
                android.widget.TextView letter=new android.widget.TextView(getContext());letter.setTextColor(0xFFF7C450);
                letter.setTypeface(android.graphics.Typeface.MONOSPACE);letter.setTextSize(14);letter.setGravity(android.view.Gravity.CENTER);
                letter.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);letter.setFocusable(false);
                int side=Math.round(32*getResources().getDisplayMetrics().density);
                glyphs[i]=letter;addView(letter,new LayoutParams(side,side));
            }
        }
        if(glyphs!=null)for(int i=0;i<glyphs.length;i++){
            glyphs[i].setVisibility(cipher.isEmpty()?GONE:VISIBLE);
            glyphs[i].setText(cipher.isEmpty()?"":String.valueOf(cipher.charAt(i)));
        }
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        if(glyphs==null)return;
        float size=Math.min(getWidth(),getHeight()),radius=size*.43f,cx=getWidth()/2f,cy=getHeight()/2f;
        int half=Math.round(16*getResources().getDisplayMetrics().density);
        for(int p=0;p<13;p++){
            // Space after the seventh letter remains a fixed gap on the ring.
            double angle=Math.toRadians(-90+(p>=7?p+1:p)*360f/14);
            int x=Math.round(cx+(float)Math.cos(angle)*radius),y=Math.round(cy+(float)Math.sin(angle)*radius);
            glyphs[p].layout(x-half,y-half,x+half,y+half);
        }
    }
    static int stackDepth(int stage){return stage<=0||stage>=7?0:stage;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float size=Math.min(getWidth(),getHeight()),left=(getWidth()-size)/2f,top=(getHeight()-size)/2f;
        int save=canvas.save();canvas.translate(left,top);canvas.scale(size/240f,size/240f);
        if(icon!=0){draw(canvas,emblem,12,12,228,228,255);canvas.restoreToCount(save);return;}
        int depth=stackDepth(stage);
        if(stage>0&&outer==null){
            inner=drawable(R.drawable.milepost_ring_inner);outer=drawable(R.drawable.milepost_ring_outer);
            layer=drawable(R.drawable.milepost_card_layer);layer.setTint(0xFF7189B8);
        }
        // Back-to-front, with an edge for each previously revealed thematic card.
        for(int i=0;i<depth;i++){
            int shift=(depth-1-i)*4;
            draw(canvas,layer,24-shift,20+shift,216-shift,212+shift,150);
        }
        float entrance=transitioning?(1-fraction)*34:0;
        int face=canvas.save();canvas.translate(0,-entrance);
        paint.setColor(0xFF242832);canvas.drawRoundRect(30,24,210,208,10,10,paint);
        if(stage>0){
            ring(canvas,outer,transitioning?360*fraction:0,34,34,206,206);
            ring(canvas,inner,transitioning?-240*fraction:0,36,36,204,204);
        }
        int alpha=transitioning?Math.min(255,(int)(fraction*400)):255;
        draw(canvas,emblem,65,65,175,175,alpha);
        if(stage>0){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.7f);paint.setColor(0xFFC7A6F6);canvas.drawCircle(120,120,57,paint);paint.setStyle(Paint.Style.FILL);}
        canvas.restoreToCount(face);canvas.restoreToCount(save);
    }
    private void ring(Canvas c,Drawable d,float angle,int l,int t,int r,int b){int s=c.save();c.rotate(angle,120,120);draw(c,d,l,t,r,b,255);c.restoreToCount(s);}
    private void draw(Canvas c,Drawable d,int l,int t,int r,int b,int alpha){d.setBounds(l,t,r,b);d.setAlpha(alpha);d.draw(c);}
}
