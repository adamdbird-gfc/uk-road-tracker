package com.roadprints.capture;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Local illustrative map: no location, network or discovery data is needed. */
public final class WelcomeDiscoveryView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean animate;
    private ValueAnimator animator;
    private float progress;
    private boolean played;
    private final Path walking = new Path(), motorway = new Path(), aRoad = new Path();

    public WelcomeDiscoveryView(Context context, boolean animate) {
        super(context); this.animate = animate;
        progress = animate && ValueAnimator.areAnimatorsEnabled() ? 0 : 1;
        setContentDescription("Example discovery: a walking path, a motorway and an A-road light up to reveal three new roads.");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        walking.moveTo(35,169);walking.cubicTo(75,169,68,125,111,124);walking.cubicTo(150,124,140,86,173,86);
        motorway.moveTo(173,86);motorway.cubicTo(208,92,220,160,274,145);motorway.cubicTo(310,135,300,76,333,60);
        aRoad.moveTo(173,86);aRoad.cubicTo(193,67,225,66,253,36);
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (played || !animate || !ValueAnimator.areAnimatorsEnabled()) { progress=1;invalidate();return; }
        played=true;animator=ValueAnimator.ofFloat(0,1);animator.setDuration(5000);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a->{progress=(float)a.getAnimatedValue();invalidate();});animator.start();
    }
    public void finishAnimation() {
        if(animator!=null){animator.cancel();animator=null;}
        played=true;progress=1;invalidate();
    }
    @Override protected void onDetachedFromWindow(){finishAnimation();super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);if(getWidth()==0 || getHeight()==0)return;
        canvas.save();canvas.scale(getWidth()/360f,getHeight()/240f);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xFFE7EEEB);
        canvas.drawRoundRect(new RectF(0,0,360,240),18,18,paint);
        Path clip=new Path();clip.addRoundRect(new RectF(0,0,360,240),18,18,Path.Direction.CW);canvas.clipPath(clip);
        paint.setColor(0xFFD1E3D5);canvas.drawOval(new RectF(8,12,113,88),paint);canvas.drawOval(new RectF(230,161,389,285),paint);
        paint.setColor(0xFFB9DADD);canvas.drawRoundRect(new RectF(-15,211,219,243),18,18,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(4);paint.setColor(0xFFC2CDCA);
        for(int x=15;x<360;x+=42)canvas.drawLine(x,0,x+40,240,paint);
        for(int y=22;y<240;y+=36)canvas.drawLine(0,y,360,y-24,paint);
        baseRoad(canvas,walking);baseRoad(canvas,motorway);baseRoad(canvas,aRoad);
        float walk=phase(progress,.08f,.43f), drive=phase(progress,.43f,.72f), road=phase(progress,.72f,.90f);
        reveal(canvas,walking,walk,0xFF101820,6);reveal(canvas,motorway,drive,0xFF176DB5,7);reveal(canvas,aRoad,road,0xFF007749,6);
        if(walk>0 && walk<1) {
            float[] position=new float[2],tangent=new float[2];PathMeasure measure=new PathMeasure(walking,false);
            measure.getPosTan(measure.getLength()*walk,position,tangent);
            canvas.save();canvas.translate(position[0],position[1]);canvas.rotate((float)Math.toDegrees(Math.atan2(tangent[1],tangent[0]))+90);
            paint.setStyle(Paint.Style.FILL);paint.setColor(0xFF27B9A9);
            canvas.drawOval(new RectF(-8,-10,-2,1),paint);canvas.drawOval(new RectF(2,-1,8,10),paint);canvas.restore();
        }
        if(progress>=.9f) {
            float celebration=phase(progress,.9f,1);
            paint.setStyle(Paint.Style.FILL);paint.setColor(0xFFF7C450);
            canvas.drawRoundRect(new RectF(68,175,292,218),14,14,paint);
            paint.setColor(0xFF0B1C50);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(17);canvas.drawText("3 new roads discovered",180,202,paint);
            paint.setColor(0xFFF7C450);paint.setStrokeWidth(2);
            for(int i=0;i<7;i++) {
                double angle=i*Math.PI*2/7;float x=180+(float)Math.cos(angle)*(28+celebration*18);
                float y=116+(float)Math.sin(angle)*(28+celebration*18);
                canvas.drawCircle(x,y,2.5f*(1-celebration)+1,paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xFFF7C450);canvas.drawCircle(35,169,6,paint);
        canvas.restore();
    }
    private void baseRoad(Canvas canvas,Path path) {
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(Color.WHITE);paint.setStrokeWidth(11);canvas.drawPath(path,paint);
        paint.setColor(0xFF9DAEAA);paint.setStrokeWidth(5);canvas.drawPath(path,paint);
    }
    private void reveal(Canvas canvas,Path path,float fraction,int color,float width) {
        if(fraction<=0)return;Path shown=new Path();PathMeasure measure=new PathMeasure(path,false);
        measure.getSegment(0,measure.getLength()*fraction,shown,true);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(Color.WHITE);paint.setStrokeWidth(width+4);canvas.drawPath(shown,paint);
        paint.setColor(color);paint.setStrokeWidth(width);canvas.drawPath(shown,paint);
    }
    private static float phase(float value,float start,float end){return Math.max(0,Math.min(1,(value-start)/(end-start)));}
}
