package com.roadprints.capture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class TimelineImportActivity extends Activity {
    private static final int PICK_TIMELINE = 81;
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF0B1C50);
        getWindow().setNavigationBarColor(0xFF0B1C50);
        buildScreen();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 56, 36, 36);
        root.setBackgroundColor(0xFF0B1C50);

        TextView title = text("Timeline data", 30, Color.WHITE, true);
        root.addView(title);

        TextView intro = text(
                "Choose your Google Timeline JSON file. It will be imported into your local Roadprints archive.",
                17, 0xFFD3DCED, false);
        intro.setPadding(0, 12, 0, 26);
        root.addView(intro);

        Button choose = new Button(this);
        choose.setText("CHOOSE TIMELINE JSON");
        choose.setTextColor(0xFF0B1C50);
        choose.setTextSize(14);
        choose.setOnClickListener(v -> chooseFile());
        root.addView(choose, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 58));

        status = text(
                "The importer will be added in the next slice. This screen confirms the Timeline route.",
                15, 0xFF67D5CC, false);
        status.setPadding(0, 28, 0, 0);
        root.addView(status);

        Button capture = new Button(this);
        capture.setText("CONTINUE TO CAPTURE");
        capture.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        LinearLayout.LayoutParams captureParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 58);
        captureParams.setMargins(0, 24, 0, 0);
        root.addView(capture, captureParams);

        setContentView(root);
    }

    private void chooseFile() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("application/json");
        startActivityForResult(picker, PICK_TIMELINE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_TIMELINE || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        String name = uri.toString();
        if (data.getData() != null) {
            android.database.Cursor cursor = getContentResolver().query(
                    uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) name = cursor.getString(0);
                } finally {
                    cursor.close();
                }
            }
        }
        status.setText("Selected: " + name + ". Ready for import in the next slice.");
    }

    private TextView text(String value, float size, int colour, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(colour);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }
}
