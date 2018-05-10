package com.mattfenlon.ghost;


import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Created by matt on 08/08/2016.
 */

public class MainService extends Service implements View.OnTouchListener {
    private static String TAG = MainService.class.getSimpleName();
    private WindowManager windowManager;
    private View floatyView;

    @Override
    public IBinder onBind(Intent intent) {
        Log.v(TAG, "[OB]");
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "[OC]");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(1, new Notification());
        }
    }

    private String displayTxt = "DEFAULT TEXT";

    @Override
    public int onStartCommand (Intent intent, int flags, int startId) {
        Log.v(TAG, "[OSC]");
        try {
            if (intent.hasExtra("show-message")) {
                String msg = intent.getStringExtra("show-message");
                Log.i(TAG, "show-message:" + msg);
                if (msg != null && !msg.isEmpty()) {
                    displayTxt = msg;
                }
            }
            windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            addOverlayView();
        } catch (Exception e) {
            Log.e(TAG, "[OSC] got Exception ", e);
        }
        return START_NOT_STICKY;
    }

    private void addOverlayView() {
        int windowType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            windowType = WindowManager.LayoutParams.TYPE_PHONE;
        }
        final WindowManager.LayoutParams params =
            new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    windowType,
                    0,
                    PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER | Gravity.START;
        params.x = 0;
        params.y = 0;
        FrameLayout interceptorLayout = new FrameLayout(this) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                // Only fire on the ACTION_DOWN event, or you'll get two events (one for _DOWN, one for _UP)
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    // Check if the HOME button is pressed
                    if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                        Log.v(TAG, "BACK Button Pressed");
                        // As we've taken action, we'll return true to prevent other apps from consuming the event as well
                        closeService();
                        return true;
                    }
                }
                // Otherwise don't intercept the event
                return super.dispatchKeyEvent(event);
            }
        };
        floatyView = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.floating_view, interceptorLayout);
        floatyView.setOnTouchListener(this);
        try {
            TextView text = floatyView.findViewById(R.id.textView);
            text.setText(displayTxt);
        } catch (Exception e) {
            Log.i(TAG, "[AOV]Cannot set text message", e);
        }
        Log.v(TAG, "[AOV] wm.addView");
        windowManager.addView(floatyView, params);
    }

    /*
    @Override
    public void onDestroy() {
        if (floatyView != null) {
            Log.v(TAG, "[OD] wm.removeView");
            windowManager.removeView(floatyView);
            floatyView = null;
        }
        super.onDestroy();
    }*/

    private void closeService() {
        synchronized (this) {
            if (floatyView != null) {
                Log.v(TAG, "[OD] wm.removeView");
                windowManager.removeView(floatyView);
                floatyView = null;
            }
            stopSelf();
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        Log.v(TAG, "onTouch...");
        // Kill service
        //onDestroy();
        closeService();
        return true;
    }
}
