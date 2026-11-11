package com.roadprints.capture;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;

final class AchievementCardStyle {
    static LinearLayout card(Context context, boolean unlocked) {
        return card(context,unlocked?0xFF3C3D3A:0xFF233B78,
                unlocked?0xFFC9A24A:0xFF46649E,14);
    }

    static LinearLayout milepostCard(Context context,boolean unlocked){
        return card(context,unlocked?0xFF213B70:0xFF192F63,0xFF46649E,10);
    }

    private static LinearLayout card(Context context,int fill,int stroke,int verticalPadding){
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context,15),dp(context,verticalPadding),dp(context,15),dp(context,verticalPadding-1));
        card.setBackground(shape(context,fill,stroke,16));
        card.setElevation(dp(context,1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,-2);
        params.bottomMargin = dp(context,9);
        card.setLayoutParams(params);
        return card;
    }

    static void badge(View badge, boolean unlocked) {
        Context context = badge.getContext();
        int padding = dp(context,10);
        badge.setPadding(padding,padding,padding,padding);
        badge.setBackground(shape(context,unlocked?0xFF5B4C2F:0xFF344A7B,0,28));
    }

    private static GradientDrawable shape(Context context, int fill, int stroke, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(context,radius));
        if (stroke != 0) shape.setStroke(dp(context,1),stroke);
        return shape;
    }

    private static int dp(Context context, int value) {
        return Math.round(value*context.getResources().getDisplayMetrics().density);
    }
}
