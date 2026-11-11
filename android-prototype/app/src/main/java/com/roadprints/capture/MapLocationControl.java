package com.roadprints.capture;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageButton;

final class MapLocationControl {
    static ImageButton add(FrameLayout frame,Runnable locate){
        Context context=frame.getContext();
        ImageButton button=new ImageButton(context);
        button.setImageResource(R.drawable.ic_map_locate);
        button.setContentDescription("Locate me");
        button.setTooltipText("Locate me");
        button.setTag("map_locate_me");
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(0xFF10275D);
        shape.setCornerRadius(dp(context,12));
        shape.setStroke(dp(context,1),0xFF46649E);
        button.setBackground(shape);
        button.setElevation(dp(context,4));
        button.setOnClickListener(view->locate.run());
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(dp(context,48),dp(context,48),
                Gravity.END|Gravity.BOTTOM);
        params.setMarginEnd(dp(context,12));
        params.bottomMargin=dp(context,34); // Clear the basemap attribution.
        frame.addView(button,params);
        return button;
    }

    /** Reused across frames. Radius is projected at the fix latitude, independent of zoom. */
    static final class Indicator {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;

        Indicator(float density){this.density=density;}

        static float radiusPixels(double latitude,double worldPixels,float accuracyMetres){
            double circumference=40_075_017*Math.cos(Math.toRadians(latitude));
            return (float)(accuracyMetres*worldPixels/circumference);
        }

        void draw(Canvas canvas,float x,float y,float radius){
            paint.setStyle(Paint.Style.FILL);
            if(radius>0){
                paint.setColor(0x332D7FE0);
                canvas.drawCircle(x,y,radius,paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(density);
                paint.setColor(0xFF2C70BD);
                canvas.drawCircle(x,y,radius,paint);
                paint.setStyle(Paint.Style.FILL);
            }
            paint.setColor(0xFFFFFFFF);
            canvas.drawCircle(x,y,7*density,paint);
            paint.setColor(0xFF2469BA);
            canvas.drawCircle(x,y,5*density,paint);
        }
    }

    private static int dp(Context context,int value){
        return Math.round(value*context.getResources().getDisplayMetrics().density);
    }
}
