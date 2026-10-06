package com.huy.pvzai;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends android.app.Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 40, 40, 40);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("PvZ AI Bridge v2\n\nAndroid-native screenshot + tap/swipe bridge");
        title.setTextSize(20f);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView info = new TextView(this);
        info.setText("Bước 1: bật Accessibility Service cho PvZ AI Bridge.\nBước 2: chạy Python agent trong Termux.\nBridge API: http://127.0.0.1:8765");
        info.setTextSize(16f);
        info.setPadding(0, 30, 0, 30);
        root.addView(info, new LinearLayout.LayoutParams(-1, -2));

        Button openSettings = new Button(this);
        openSettings.setText("MỞ CÀI ĐẶT ACCESSIBILITY");
        openSettings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(openSettings, new LinearLayout.LayoutParams(-1, -2));

        Button test = new Button(this);
        test.setText("KIỂM TRA BRIDGE");
        test.setOnClickListener(v -> {
            Intent intent = new Intent(this, PvzAccessibilityService.class);
            // No-op: service is controlled by Android after enabling Accessibility.
        });
        root.addView(test, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
}
