package com.mattfenlon.ghost;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.PixelFormat;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.io.FileDescriptor;

public class MainService extends Service implements View.OnTouchListener {
    private static String TAG = MainService.class.getSimpleName();
    private WindowManager windowManager;
    private LayoutInflater layoutInflater;
    private View floatyView;
    private String displayTxt = "DEFAULT TEXT";
    private HandlerThread handlerThread;
    private Handler handler;
    private Object mpLock = new Object();
    private MediaPlayer mp;
    private int windowType;
    private AudioAttributes audAttr;
    private WindowManager.LayoutParams wmLayoutParam;

    public static final String EXTRA_SHOW_MESSAGE = "show-message";
    public static final String EXTRA_SHOW_MESSAGE_AND_RINGTON = "show-message-and-rington";
    public static final String EXTRA_CLEAR_MESSAGE = "clear-message";
    public static final String EXTRA_STOP_SERVICE = "stop-service";

    @Override
    public IBinder onBind(Intent intent) {
        Log.v(TAG, "[OB]");
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "[OC]");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Log.v(TAG, "[OC] Your system is above LOLLIPOP");
            audAttr = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            Log.v(TAG, "[OC] Your system is above OREO");
            startForeground(1, new Notification());
        } else {
            windowType = WindowManager.LayoutParams.TYPE_PHONE;
        }
        wmLayoutParam = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                0,
                PixelFormat.TRANSLUCENT);
        wmLayoutParam.gravity = Gravity.CENTER | Gravity.START;
        wmLayoutParam.x = 0;
        wmLayoutParam.y = 0;
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        layoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        try {
            handlerThread = new HandlerThread("service-thread");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
        } catch (Exception e) {
            Log.e(TAG, "[OC] service thread error:" +e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "[OD]");
        try {
            if (null != handler) {
                handler.removeCallbacksAndMessages(null);
            }
            if (null != handlerThread) {
                handlerThread.quitSafely();
            }
        } finally {
            audAttr = null;
            wmLayoutParam = null;
            windowManager = null;
            layoutInflater = null;
            handlerThread = null;
            handler = null;
            super.onDestroy();
        }
    }

    @Override
    public int onStartCommand (Intent intent, int flags, int startId) {
        Log.v(TAG, "[OSC]");
        try {
            if (intent.hasExtra(EXTRA_SHOW_MESSAGE)) {
                onShowMessage(intent);
            } else if (intent.hasExtra(EXTRA_SHOW_MESSAGE_AND_RINGTON)) {
                onShowMessage(intent);
            } else if (intent.hasExtra(EXTRA_CLEAR_MESSAGE)) {
                Log.v(TAG, EXTRA_CLEAR_MESSAGE);
                onRemoveMessage();
            } else if (intent.hasExtra(EXTRA_STOP_SERVICE)) {
                Log.v(TAG, EXTRA_STOP_SERVICE);
                onStopService();
            }
        } catch (Exception e) {
            Log.e(TAG, "[OSC] got exception ", e);
        }
        return START_NOT_STICKY;
    }

    private void onShowMessage(Intent intent) {
        Log.v(TAG, "[OSM]" + intent);
        String msg = null;
        if (null == msg) msg = intent.getStringExtra(EXTRA_SHOW_MESSAGE);
        if (!TextUtils.isEmpty(msg)) {
            Log.v(TAG, EXTRA_SHOW_MESSAGE + "=>" + msg);
            displayTxt = msg;
        }
        if (null == msg) {
            msg = intent.getStringExtra(EXTRA_SHOW_MESSAGE_AND_RINGTON);
            if (!TextUtils.isEmpty(msg)) {
                Log.v(TAG, EXTRA_SHOW_MESSAGE_AND_RINGTON + "=>" + msg);
                displayTxt = msg;
                // mp3 from AOSP
                // https://android.googlesource.com/platform/frameworks/base/+/android-cts-7.1_r23/media/tests/contents/media_api/videoeditor/
                playMusic("MP3_48KHz_128kbps_s_1_17.mp3");
            }
        }
        addOverlayView();
    }

    private void onRemoveMessage() {
        Log.v(TAG, "[ORM]");
        removeOverlayView();
        stopMusic();
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        Log.v(TAG, "[OT]");
        onRemoveMessage();
        return true;
    }

    private void stopMusic() {
        Log.v(TAG, "[MUSIC] in stop");
        synchronized (mpLock) {
            if (null != mp) {
                if (mp.isPlaying()) {
                    Log.v(TAG, "[MUSIC] stop mp");
                    mp.stop();
                }
                Log.v(TAG, "[MUSIC] release mp");
                mp.release();
                mp = null;
            }
        }
    }

    private void playMusic(final String assetPath) {
        stopMusic();
        handler.post(new Runnable() {
            public void run() {
                Log.v(TAG, "[MUSIC] in start");
                try {
                    AssetManager asset = getApplication().getApplicationContext().getAssets();
                    Log.v(TAG, "[MUSIC] AssetManager=" + asset);
                    FileDescriptor fd = asset.openFd(assetPath).getFileDescriptor();
                    Log.v(TAG, "[MUSIC] InputStream=" + fd);
                    synchronized (mpLock) {
                        Log.v(TAG, "[MUSIC] new mp");
                        mp = new MediaPlayer();
                        Log.v(TAG, "[MUSIC] test set audio type");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            mp.setAudioAttributes(audAttr);
                        } else {
                            mp.setAudioStreamType(AudioManager.STREAM_RING);
                        }
                        mp.setDataSource(fd);
                        mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                            @Override
                            public void onPrepared(MediaPlayer mediaPlayer) {
                                Log.v(TAG, "[MUSIC] start play music");
                                mp.start();
                            }
                        });
                        mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                            @Override
                            public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
                                Log.v(TAG, "[MUSIC] play music got error " + i + " " + i1);
                                stopMusic();
                                return false;
                            }
                        });
                        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            @Override
                            public void onCompletion(MediaPlayer mediaPlayer) {
                                Log.v(TAG, "[MUSIC] play music ended");
                                stopMusic();
                            }
                        });
                        Log.v(TAG, "[MUSIC] prepareAsync");
                        mp.prepareAsync();
                    }
                    Log.v(TAG, "[MUSIC] start end");
                } catch (Exception e) {
                    Log.e(TAG, "[MUSIC] got exception", e);
                }
            }
        });
    }

    private void changeTextOnOverlayView(String text) {
        try {
            TextView view = floatyView.findViewById(R.id.textView);
            view.setText(text);
        } catch (Exception e) {
            Log.i(TAG, "Cannot change text on OverlayView", e);
        }
    }

    private View createOverlayView() {
        View view;
        FrameLayout interceptorLayout = new FrameLayout(this) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                // Only fire on the ACTION_DOWN event, or you'll get two events (one for _DOWN, one for _UP)
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    // Check if the HOME button is pressed
                    if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                        Log.v(TAG, "BACK Button Pressed");
                        // As we've taken action, we'll return true to prevent other apps from consuming the event as well
                        onRemoveMessage();
                        return true;
                    }
                }
                // Otherwise don't intercept the event
                return super.dispatchKeyEvent(event);
            }
        };
        view = layoutInflater.inflate(R.layout.floating_view, interceptorLayout);
        view.setOnTouchListener(this);
        return view;
    }

    private void addOverlayView() {
        synchronized (this) {
            if (null == floatyView) {
                Log.v(TAG, "[AOV] wm.addView");
                windowManager.addView(floatyView = createOverlayView(), wmLayoutParam);
            }
        }
        changeTextOnOverlayView(displayTxt);
    }

    private void removeOverlayView() {
        synchronized (this) {
            if (null != windowManager) {
                Log.v(TAG, "[CS] wm.removeView");
                windowManager.removeView(floatyView);
            }
            floatyView = null;
        }
    }

    private void onStopService() {
        Log.v(TAG, "[OSS]");
        onRemoveMessage();
        stopSelf();
    }
}
