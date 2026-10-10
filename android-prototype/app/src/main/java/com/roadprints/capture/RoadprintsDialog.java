package com.roadprints.capture;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

final class RoadprintsDialog {
    static AlertDialog show(AlertDialog.Builder builder) {
        AlertDialog dialog=builder.create();
        dialog.setOnShowListener(ignored -> {
            if(dialog.getWindow()==null)return;
            GradientDrawable panel=new GradientDrawable();panel.setColor(0xFF0B1C50);
            panel.setCornerRadius(dp(dialog,22));panel.setStroke(dp(dialog,1),0xFF496096);
            dialog.getWindow().setBackgroundDrawable(panel);
            tint(dialog.getWindow().getDecorView());
            for(int which:new int[]{AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL,AlertDialog.BUTTON_POSITIVE}) {
                Button button=dialog.getButton(which);if(button==null)continue;
                boolean primary=which==AlertDialog.BUTTON_POSITIVE;
                button.setTextColor(primary?0xFF0B1C50:0xFF67D5CC);
                button.setTextSize(13);button.setTypeface(null,android.graphics.Typeface.BOLD);
                GradientDrawable background=new GradientDrawable();background.setColor(primary?0xFFF7C450:0xFF233B78);
                background.setCornerRadius(dp(dialog,12));button.setBackground(background);
                button.setPadding(dp(dialog,12),dp(dialog,8),dp(dialog,12),dp(dialog,8));
            }
        });
        dialog.show();return dialog;
    }
    private static void tint(View view) {
        if(view instanceof TextView)((TextView)view).setTextColor(Color.WHITE);
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)tint(((ViewGroup)view).getChildAt(i));
    }
    private static int dp(AlertDialog d,int n){return Math.round(n*d.getContext().getResources().getDisplayMetrics().density);}
}
