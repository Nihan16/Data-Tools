package com.bubbleapp.aide;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class FloatingBubbleService extends Service {

    public static final String ACTION_START       = "ACTION_START";
    public static final String ACTION_STOP        = "ACTION_STOP";
    public static final String ACTION_UPDATE_MODE = "ACTION_UPDATE_MODE";

    private static final String CHANNEL_ID = "BubbleChannel";
    private static final String TAG        = "FloatingBubbleService";

    // GAS endpoint
    private static final String GAS_URL =
        "https://script.google.com/macros/s/AKfycbyhlsZ3oqZK9sb2oP6VcKJJ5pRZQL45e47ozqxsXd8dWHezHrc08GZvsCvVzts_Hdia/exec";

    private WindowManager windowManager;
    private View bubbleView;       // Small floating bubble icon
    private View chatView;         // Full floating chat window

    private boolean instaModeActive = false;
    private boolean chatOpen        = false;

    // Drag tracking
    private int initialX, initialY;
    private float initialTouchX, initialTouchY;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;

        String action = intent.getAction();
        if (action == null) action = ACTION_START;

        switch (action) {
            case ACTION_START:
                instaModeActive = intent.getBooleanExtra("insta_mode", false);
                showBubble();
                startForeground(1, buildNotification());
                break;

            case ACTION_STOP:
                removeBubble();
                stopSelf();
                break;

            case ACTION_UPDATE_MODE:
                instaModeActive = intent.getBooleanExtra("insta_mode", false);
                break;
        }

        return START_STICKY;
    }

    // -----------------------------------------------------------------------
    // Bubble Icon
    // -----------------------------------------------------------------------
    private void showBubble() {
        if (bubbleView != null) return;

        LayoutInflater inflater = LayoutInflater.from(this);
        bubbleView = inflater.inflate(R.layout.layout_bubble, null);

        final WindowManager.LayoutParams bubbleParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.LEFT;
        bubbleParams.x = 0;
        bubbleParams.y = 200;

        windowManager.addView(bubbleView, bubbleParams);

        // Drag + click on bubble
        bubbleView.setOnTouchListener(new View.OnTouchListener() {
            long lastDownTime = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = bubbleParams.x;
                        initialY = bubbleParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        lastDownTime = System.currentTimeMillis();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        bubbleParams.x = initialX + (int)(event.getRawX() - initialTouchX);
                        bubbleParams.y = initialY + (int)(event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(bubbleView, bubbleParams);
                        return true;

                    case MotionEvent.ACTION_UP:
                        long duration = System.currentTimeMillis() - lastDownTime;
                        float dx = Math.abs(event.getRawX() - initialTouchX);
                        float dy = Math.abs(event.getRawY() - initialTouchY);
                        if (duration < 300 && dx < 10 && dy < 10) {
                            // It's a tap → open chat
                            toggleChat(bubbleParams.x, bubbleParams.y);
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void removeBubble() {
        if (chatView != null) {
            windowManager.removeView(chatView);
            chatView = null;
        }
        if (bubbleView != null) {
            windowManager.removeView(bubbleView);
            bubbleView = null;
        }
    }

    // -----------------------------------------------------------------------
    // Chat Window
    // -----------------------------------------------------------------------
    private void toggleChat(int bx, int by) {
        if (chatOpen) {
            closeChat();
        } else {
            openChat(bx, by);
        }
    }

    private void openChat(int bx, int by) {
        if (chatView != null) return;

        LayoutInflater inflater = LayoutInflater.from(this);
        chatView = inflater.inflate(R.layout.layout_chat, null);

        WindowManager.LayoutParams chatParams = new WindowManager.LayoutParams(
            900,
            1100,
            windowType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        );
        chatParams.gravity = Gravity.TOP | Gravity.LEFT;
        chatParams.x = Math.max(0, bx - 100);
        chatParams.y = Math.max(0, by - 600);

        windowManager.addView(chatView, chatParams);
        chatOpen = true;

        // Refs inside chat layout
        final TextView tvChat    = (TextView) chatView.findViewById(R.id.tv_chat_log);
        final EditText etInput   = (EditText) chatView.findViewById(R.id.et_input);
        final Button   btnSend   = (Button)   chatView.findViewById(R.id.btn_send);
        final Button   btnClose  = (Button)   chatView.findViewById(R.id.btn_close_chat);
        final ScrollView scrollView = (ScrollView) chatView.findViewById(R.id.scroll_chat);

        if (instaModeActive) {
            tvChat.append("[Insta Mode ON]\n");
        } else {
            tvChat.append("[Normal Mode — tap Send to chat]\n");
        }

        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeChat();
            }
        });

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String userText = etInput.getText().toString().trim();
                if (userText.isEmpty()) return;

                tvChat.append("\nYou: " + userText + "\n");
                etInput.setText("");

                scrollToBottom(scrollView);

                if (instaModeActive) {
                    // Append star and send to GAS
                    String query = userText + "*";
                    tvChat.append("Sending to Insta Mode...\n");
                    scrollToBottom(scrollView);
                    sendToGAS(query, tvChat, scrollView);
                }
            }
        });
    }

    private void closeChat() {
        if (chatView != null) {
            windowManager.removeView(chatView);
            chatView = null;
        }
        chatOpen = false;
    }

    // -----------------------------------------------------------------------
    // GAS Network Call
    // -----------------------------------------------------------------------
    private void sendToGAS(final String query,
                           final TextView tvChat,
                           final ScrollView scrollView) {

        final Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(new Runnable() {
            @Override
            public void run() {
                String result = "";
                HttpURLConnection conn = null;
                try {
                    String encoded = URLEncoder.encode(query, "UTF-8");
                    String urlStr  = GAS_URL + "?send=" + encoded;
                    URL url = new URL(urlStr);

                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setInstanceFollowRedirects(true);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);

                    int code = conn.getResponseCode();
                    BufferedReader br;
                    if (code == HttpURLConnection.HTTP_OK) {
                        br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    } else {
                        br = new BufferedReader(
                            new InputStreamReader(conn.getErrorStream()));
                    }

                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    br.close();
                    result = sb.toString().trim();

                } catch (Exception e) {
                    Log.e(TAG, "GAS request failed", e);
                    result = "[Error: " + e.getMessage() + "]";
                } finally {
                    if (conn != null) conn.disconnect();
                }

                final String finalResult = result;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (tvChat != null) {
                            tvChat.append("Bot: " + finalResult + "\n");
                            scrollToBottom(scrollView);
                        }
                    }
                });
            }
        }).start();
    }

    private void scrollToBottom(final ScrollView scrollView) {
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------
    private int windowType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        return WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Bubble Service",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager nm =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        builder.setContentTitle("Bubble App")
               .setContentText("Bubble is active")
               .setSmallIcon(android.R.drawable.ic_dialog_info);
        return builder.build();
    }

    @Override
    public void onDestroy() {
        removeBubble();
        super.onDestroy();
    }
}
