package com.mattfenlon.ghost;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class MainService extends Service implements View.OnTouchListener {
    private static String TAG = MainService.class.getSimpleName();

    public static final String EXTRA_SHOW_MESSAGE = "show-message";
    public static final String EXTRA_SHOW_MESSAGE_AND_RINGTONE = "show-message-and-ringtone";
    public static final String EXTRA_SHOW_MESSAGE_AND_MUSIC = "show-message-and-music";
    public static final String EXTRA_SHOW_MESSAGE_AND_MOVIE = "show-message-and-movie";
    public static final String EXTRA_PLAY_MOVIE_URL = "play-movie-url";
    public static final String EXTRA_PLAY_MUSIC_URL = "play-music-url";
    public static final String EXTRA_VALUE_NULL_URL = "<null>";
    public static final String EXTRA_CLEAR_MESSAGE = "clear-message";
    public static final String EXTRA_STOP_SERVICE = "stop-service";

    private WindowManager windowManager;
    private LayoutInflater layoutInflater;
    private View floatyView;
    private String displayTxt = "DEFAULT TEXT";
    private int windowType;
    private WindowManager.LayoutParams wmLayoutParam;
    private MainPlayer player;
    private SurfaceHolder holderForVideoPlayback = null;
    private SurfaceHolder surfaceHolder;

    private SurfaceHolder.Callback holderCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.d(TAG, "surfaceCreated");
            holderForVideoPlayback = holder;
        }
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.d(TAG, "surfaceChanged");
        }
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.d(TAG, "surfaceDestroyed");
            holderForVideoPlayback = null;
        }
    };

    private MainPlayer.Callback playerCallback = new MainPlayer.Callback() {
        @Override
        public SurfaceHolder onDisplayRequired() {
            long timeout = SystemClock.uptimeMillis() + 5000; // timeout 5 sec
            while (null == holderForVideoPlayback) {
                Log.d(TAG, "[ODR] retry=" + holderForVideoPlayback);
                if (SystemClock.uptimeMillis()  > timeout) {
                    Log.e(TAG, "[ODR] Timeout");
                    break;
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Log.d(TAG, "[ODR] InterruptedException ", e);
                }
            }
            Log.d(TAG, "[ODR] " + holderForVideoPlayback);
            return holderForVideoPlayback;
        }
        @Override
        public void onError(MainPlayer player, int i, int i1) {
            Log.v(TAG, "play got error " + i + " " + i1);
            player.stop();
            Toast.makeText(getApplicationContext(), "playback error " + i + ","+ i1, Toast.LENGTH_LONG).show();
        }
        @Override
        public void onStart(MainPlayer player) {
            Toast.makeText(getApplicationContext(), "playback started!", Toast.LENGTH_LONG).show();
        }
        @Override
        public void onCompletion(MainPlayer player) {
            Toast.makeText(getApplicationContext(), "playback completion!", Toast.LENGTH_LONG).show();
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        Log.v(TAG, "[OB]");
        return null;
    }

    // https://stackoverflow.com/questions/47531742/startforeground-fail-after-upgrade-to-android-8-1
    private void startMyOwnForeground() {
        String NOTIFICATION_CHANNEL_ID = getApplicationContext().getPackageName();
        String channelName = getClass().getSimpleName();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel chan = new android.app.NotificationChannel(
                    NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
            chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            assert manager != null;
            manager.createNotificationChannel(chan);
        } else {
            Log.d(TAG, "system below Oreo, no NotificationChannel");
        }

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.drawable.ic_android_black_24dp) // notification icon
                .setContentTitle("App is running in background") // notification text
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(1, notification);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "[OC]");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            Log.v(TAG, "[OC] Your system is above OREO");
            //startForeground(1, new Notification());
            // above workaround crashed in android 9 (Pie)
            // android.app.RemoteServiceException: Bad notification for startForeground: java.lang.RuntimeException: invalid channel for service notification: Notification(channel=null pri=0 contentView=null vibrate=null sound=null defaults=0x0 flags=0x40 color=0x00000000 vis=PRIVATE)
            startMyOwnForeground();
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
        // if you want to let the other app get the touch event, you should add FLAG_NOT_TOUCH_MODAL
        wmLayoutParam.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        // if you want to let this app get the back key event to leave your app, you should NOT add FLAG_NOT_FOCUSABLE
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        layoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        player = new MainPlayer(getApplication().getApplicationContext(), playerCallback);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "[OD]");
        try {
            if (null != player) {
                player.release();
            }
        } finally {
            player = null;
        }
        wmLayoutParam = null;
        windowManager = null;
        layoutInflater = null;
        super.onDestroy();
    }

    @Override
    public int onStartCommand (Intent intent, int flags, int startId) {
        Log.v(TAG, "[OSC]");
        try {
            if (intent.hasExtra(EXTRA_SHOW_MESSAGE)) {
                onShowMessage(intent);
            } else if (intent.hasExtra(EXTRA_SHOW_MESSAGE_AND_RINGTONE)) {
                onShowMessage(intent);
            } else if (intent.hasExtra(EXTRA_SHOW_MESSAGE_AND_MUSIC)) {
                onShowMessage(intent);
            } else if (intent.hasExtra(EXTRA_SHOW_MESSAGE_AND_MOVIE)) {
                onShowMessage(intent);
            } else if (intent.hasExtra(EXTRA_PLAY_MOVIE_URL)) {
                onPlayUrl(intent);
            } else if (intent.hasExtra(EXTRA_PLAY_MUSIC_URL)) {
                onPlayUrl(intent);
            } else if (intent.hasExtra(EXTRA_CLEAR_MESSAGE)) {
                onRemoveMessage();
            } else if (intent.hasExtra(EXTRA_STOP_SERVICE)) {
                onStopService();
            }
        } catch (Exception e) {
            Log.e(TAG, "[OSC] got exception ", e);
        }
        return START_NOT_STICKY;
    }

    private boolean onPlayUrl(Intent intent) {
        Log.v(TAG, "[OPU]" + intent);
        String msg = null;
        if (intent.hasExtra(EXTRA_PLAY_MOVIE_URL)) {
            msg = intent.getStringExtra(EXTRA_PLAY_MOVIE_URL);
            if (!TextUtils.isEmpty(msg) && !EXTRA_VALUE_NULL_URL.equals(msg)) {
                Log.d(TAG, "Play video url=" + msg);
                displayTxt = msg;
                player.playURL(msg, MainPlayer.TYPE_VIDEO);
            } else {
                // mp4 from AOSP
                // https://android.googlesource.com/platform/frameworks/base/+/android-cts-7.1_r23/media/tests/contents/media_api/videoeditor/
                displayTxt = "H264_BP_1920x1080_30fps_1200Kbps_1_10.mp4";
                player.playAsset("H264_BP_1920x1080_30fps_1200Kbps_1_10.mp4", MainPlayer.TYPE_VIDEO);
            }
            addOverlayView();
            return true;
        } else if (intent.hasExtra(EXTRA_PLAY_MUSIC_URL)) {
            msg = intent.getStringExtra(EXTRA_PLAY_MUSIC_URL);
            if (!TextUtils.isEmpty(msg) && !EXTRA_VALUE_NULL_URL.equals(msg)) {
                Log.d(TAG, "Play music url=" + msg);
                displayTxt = msg;
                player.playURL(msg, MainPlayer.TYPE_MUSIC);
            } else {
                // mp3 from AOSP
                // https://android.googlesource.com/platform/frameworks/base/+/android-cts-7.1_r23/media/tests/contents/media_api/videoeditor/
                displayTxt = "MP3_48KHz_128kbps_s_1_17.mp3";
                player.playAsset("MP3_48KHz_128kbps_s_1_17.mp3", MainPlayer.TYPE_MUSIC);
            }
            addOverlayView();
            return true;
        }
        return false;
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
            msg = intent.getStringExtra(EXTRA_SHOW_MESSAGE_AND_MUSIC);
            if (!TextUtils.isEmpty(msg)) {
                Log.v(TAG, EXTRA_SHOW_MESSAGE_AND_MUSIC + "=>" + msg);
                displayTxt = msg;
                // mp3 from AOSP
                // https://android.googlesource.com/platform/frameworks/base/+/android-cts-7.1_r23/media/tests/contents/media_api/videoeditor/
                player.playAsset("MP3_48KHz_128kbps_s_1_17.mp3", MainPlayer.TYPE_MUSIC);
            }
        }
        if (null == msg) {
            msg = intent.getStringExtra(EXTRA_SHOW_MESSAGE_AND_RINGTONE);
            if (!TextUtils.isEmpty(msg)) {
                Log.v(TAG, EXTRA_SHOW_MESSAGE_AND_RINGTONE + "=>" + msg);
                displayTxt = msg;
                //player.playRingtone(RingtoneManager.TYPE_NOTIFICATION); // nul uri
                player.playRingtone(RingtoneManager.TYPE_RINGTONE);  // null uri
            }
        }
        if (null == msg) {
            msg = intent.getStringExtra(EXTRA_SHOW_MESSAGE_AND_MOVIE);
            if (!TextUtils.isEmpty(msg)) {
                Log.v(TAG, EXTRA_SHOW_MESSAGE_AND_MOVIE + "=>" + msg);
                displayTxt = msg;
                // mp4 from AOSP
                // https://android.googlesource.com/platform/frameworks/base/+/android-cts-7.1_r23/media/tests/contents/media_api/videoeditor/
                player.playAsset("H264_BP_1920x1080_30fps_1200Kbps_1_10.mp4", MainPlayer.TYPE_VIDEO);
            }
        }
        addOverlayView();
    }

    private void onRemoveMessage() {
        Log.v(TAG, "[ORM]");
        removeOverlayView();
        if (null != player) player.stop();
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
            StringBuilder sb;
            final float x = motionEvent.getX();
            final float y = motionEvent.getY();
            sb = new StringBuilder();
            Log.v(TAG, sb.append("touch x=").append(x).append(" y=").append(y).toString());
            if (null != floatyView) {
                // https://stackoverflow.com/questions/9125935/get-position-of-imageview-on-layout-android
                ImageView imgView = floatyView.findViewById(R.id.BgImageOnFloaty);
                final int imgL = imgView.getLeft();
                final int imgR = imgView.getRight();
                final int imgT = imgView.getTop();
                final int imgB = imgView.getBottom();
                boolean isTouched = ( x>imgL &&  x<imgR && y>imgT && y<imgB );
                sb = new StringBuilder();
                Log.v(TAG, sb.append("Touched?").append(isTouched)
                        .append(" img ").append(imgL).append(',').append(imgR).append(',').append(imgT).append(',').append(imgB)
                        .append(" w,h=").append(imgR-imgL).append(',').append(imgB-imgT)
                                .toString());
                if (isTouched) onRemoveMessage();
                return isTouched;
            }
        }
        return false;
    }

    private void changeTextOnOverlayView(String text) {
        try {
            TextView view = floatyView.findViewById(R.id.TextOnFloaty);
            view.setText(text);
        } catch (Exception e) {
            Log.i(TAG, "Cannot change text on OverlayView", e);
        }
    }

    private View createOverlayView() {
        View view;
        view = layoutInflater.inflate(R.layout.floating_view, new FrameLayout(this) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                Log.v(TAG, "dispatchTouchEvent " + ev);
                boolean ret = super.dispatchTouchEvent(ev);
                Log.d(TAG, "dispatchTouchEvent ret=" + ret + " " + ev);
                return ret;
            }
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                // Only fire on the ACTION_DOWN event, or you'll get two events (one for _DOWN, one for _UP)
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    // Check if the HOME button is pressed
                    if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                        Log.v(TAG, "BACK Button Pressed, ret:true");
                        // As we've taken action, we'll return true to prevent other apps from consuming the event as well
                        onRemoveMessage();
                        return true;
                    }
                    /* test code beg */
                    if (event.getKeyCode() == KeyEvent.KEYCODE_2) {
                        Log.v(TAG, "Number 2 Pressed, ret:false");
                        return false;
                    }
                    /* test code end */
                }
                // Otherwise don't intercept the event
                boolean ret = super.dispatchKeyEvent(event);
                /* test code beg */
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    Log.v(TAG, "key:" + event + " ret:" + ret);
                }
                /* test code end */
                return ret;
            }
        });
        SurfaceView surfaceView = view.findViewById(R.id.SurfaceViewOnFloaty);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(holderCallback);
        view.setOnTouchListener(this);
        Button btn = view.findViewById(R.id.ButtonOnFloaty);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i(TAG, "clicked");
                onRemoveMessage();
            }
        });
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
                Log.v(TAG, "[ROV] wm.removeView");
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
