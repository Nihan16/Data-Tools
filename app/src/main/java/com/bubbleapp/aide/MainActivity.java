package com.bubbleapp.aide;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int OVERLAY_PERMISSION_REQ_CODE = 1234;

    private Button btnEnable, btnDisable, btnInstaMode;
    private TextView tvStatus, tvInstaModeStatus;
    private SharedPreferences prefs;

    private boolean instaModeActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("BubbleAppPrefs", MODE_PRIVATE);
        instaModeActive = prefs.getBoolean("insta_mode", false);

        btnEnable      = (Button) findViewById(R.id.btn_enable);
        btnDisable     = (Button) findViewById(R.id.btn_disable);
        btnInstaMode   = (Button) findViewById(R.id.btn_insta_mode);
        tvStatus       = (TextView) findViewById(R.id.tv_status);
        tvInstaModeStatus = (TextView) findViewById(R.id.tv_insta_mode_status);

        updateInstaModeUI();

        // ---- Enable Bubble ----
        btnEnable.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!hasOverlayPermission()) {
                    requestOverlayPermission();
                } else {
                    startBubbleService();
                }
            }
        });

        // ---- Disable Bubble ----
        btnDisable.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopBubbleService();
            }
        });

        // ---- Insta Mode Toggle ----
        btnInstaMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                instaModeActive = !instaModeActive;
                prefs.edit().putBoolean("insta_mode", instaModeActive).apply();
                updateInstaModeUI();

                // Notify running service about mode change
                Intent intent = new Intent(MainActivity.this, FloatingBubbleService.class);
                intent.setAction(FloatingBubbleService.ACTION_UPDATE_MODE);
                intent.putExtra("insta_mode", instaModeActive);
                startService(intent);
            }
        });
    }

    private boolean hasOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
            );
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (hasOverlayPermission()) {
                startBubbleService();
            } else {
                Toast.makeText(this,
                    "Overlay permission denied. Bubble cannot be shown.",
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startBubbleService() {
        Intent intent = new Intent(this, FloatingBubbleService.class);
        intent.setAction(FloatingBubbleService.ACTION_START);
        intent.putExtra("insta_mode", instaModeActive);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        tvStatus.setText("Bubble: ENABLED");
        tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        Toast.makeText(this, "Bubble Enabled!", Toast.LENGTH_SHORT).show();
    }

    private void stopBubbleService() {
        Intent intent = new Intent(this, FloatingBubbleService.class);
        intent.setAction(FloatingBubbleService.ACTION_STOP);
        startService(intent);
        tvStatus.setText("Bubble: DISABLED");
        tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        Toast.makeText(this, "Bubble Disabled!", Toast.LENGTH_SHORT).show();
    }

    private void updateInstaModeUI() {
        if (instaModeActive) {
            btnInstaMode.setText("Insta Mode: ON");
            tvInstaModeStatus.setText("Insta Mode is ACTIVE");
            tvInstaModeStatus.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
        } else {
            btnInstaMode.setText("Insta Mode: OFF");
            tvInstaModeStatus.setText("Insta Mode is INACTIVE");
            tvInstaModeStatus.setTextColor(getResources().getColor(android.R.color.darker_gray));
        }
    }
}
