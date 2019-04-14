package com.mattfenlon.ghost;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
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
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;

public class MainService extends Service implements View.OnTouchListener {
    private static String TAG = MainService.class.getSimpleName();

    public static String ACTION_FLOATING_WINDOWS = "tw.com.chttl.FloatingWindows.message";

    public static final String EXTRA_SHOW_MESSAGE = "show-message";
    public static final String EXTRA_SHOW_MESSAGE_AND_RINGTONE = "show-message-and-ringtone";
    public static final String EXTRA_SHOW_MESSAGE_AND_MUSIC = "show-message-and-music";
    public static final String EXTRA_SHOW_MESSAGE_AND_MOVIE = "show-message-and-movie";
    public static final String EXTRA_PLAY_MOVIE_URL = "play-movie-url";
    public static final String EXTRA_PLAY_MOVIE_AND_CAMERA_PREVIEW_URL = "play-movie-url-and-camera-preview";
    public static final String EXTRA_PLAY_MUSIC_URL = "play-music-url";
    public static final String EXTRA_VALUE_NULL_URL = "<null>";
    public static final String EXTRA_CLEAR_MESSAGE = "clear-message";
    public static final String EXTRA_STOP_SERVICE = "stop-service";

    private WindowManager windowManager;
    private LayoutInflater layoutInflater;
    private View floatyView;
    private String displayTxt = "DEFAULT TEXT";
    private boolean enableCameraPreview = false;
    private int windowType;
    private WindowManager.LayoutParams wmLayoutParam;
    private MainPlayer player;
    private SurfaceHolder holderForVideoPlayback = null;
    private SurfaceHolder surfaceHolder;
    private SurfaceHolder holderForCamera = null;
    private SurfaceHolder camHolder;
    private HandlerThread handlerThread;
    private Handler handler;
    private Camera camera;

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

    private SurfaceHolder.Callback camHolderCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.d(TAG, "camView Created");
            holderForCamera = holder;
            try {
                camera.setPreviewDisplay(holderForCamera);
                camera.startPreview();
            } catch (IOException e) {
                Log.d(TAG, "Error setting camera preview: " + e.getMessage());
            }
        }
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.d(TAG, "camView Changed");
            if (holder.getSurface() == null){
                // preview surface does not exist
                return;
            }

            // stop preview before making changes
            try {
                camera.stopPreview();
            } catch (Exception e){
                // ignore: tried to stop a non-existent preview
            }

            // set preview size and make any resize, rotate or
            // reformatting changes here
            holderForCamera = holder;

            // start preview with new settings
            try {
                camera.setPreviewDisplay(holderForCamera);
                camera.startPreview();
            } catch (Exception e){
                Log.d(TAG, "Error starting camera preview: " + e.getMessage());
            }

        }
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.d(TAG, "camView Destroyed");
            try {
                camera.stopPreview();
            } finally {
                holderForCamera = null;
            }
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

    public void enableCameraPreview(boolean enable) {
        Log.v(TAG, "[ECP]" + enable);
        this.enableCameraPreview = enable;
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
        handlerThread = new HandlerThread("service-thread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "[OD]");
        try {
            if (null != handler) {
                handlerThread.quitSafely();
                handlerThread.join(3000);
            }
        } catch (InterruptedException e) {
            Log.d(TAG, "stop service thread exception", e);
        } finally {
            handlerThread = null;
        }
        try {
            if (null != handler) {
                handler.removeCallbacksAndMessages(null);
            }
        } finally {
            handler = null;
        }
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
            if (ACTION_FLOATING_WINDOWS.equals(intent.getAction())) {
                onVSMFloatingWindowProcess(intent);
            } else if (intent.hasExtra(EXTRA_SHOW_MESSAGE)) {
                onShowMessage(intent);
            } else if (intent.hasExtra(EXTRA_SHOW_MESSAGE_AND_RINGTONE)) {
                onShowMessage(intent);
            } else if (intent.hasExtra(EXTRA_SHOW_MESSAGE_AND_MUSIC)) {
                onShowMessage(intent);
            } else if (intent.hasExtra(EXTRA_SHOW_MESSAGE_AND_MOVIE)) {
                onShowMessage(intent);
            } else if (intent.hasExtra(EXTRA_PLAY_MOVIE_URL)) {
                onPlayUrl(intent);
            } else if (intent.hasExtra(EXTRA_PLAY_MOVIE_AND_CAMERA_PREVIEW_URL)) {
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

    final static int DEFAULT_TIMEOUT = -1;

    // additional extras for videocall
    public final static String EXTRA_VIDEOCALL_HANGHUP = "hangup";
    public final static String EXTRA_VIDEOCALL_PICKUP = "pickup";
    public final static String EXTRA_VIDEOCALL_CANCEL = "cancel";

    public final static String TYPE_NORMAL = "normal"; // 一般訊息
    public final static String TYPE_WARNING = "warning"; // 緊急訊息
    public final static String TYPE_CLOSE = "close"; // 中斷/關閉視訊訊息
    public final static String TYPE_VIDEOCALL_CALLING =
            "videocall_calling@videocall.reply.cancel"; // 視訊撥出訊息
    public final static String TYPE_VIDEOCALL_INCOMING =
            "videocall_calling@videocall.reply.pickup$videocall.reply.hangup"; // 視訊撥入訊息

    public final static String ACTION_VIDEOCALL_FLOATING_WINDOW_REPLY = "com.modstb.extension.videocall.action.fwreply";
    public final static String EXTRA_VIDEOCALL_FLOATING_WINDOW_REPLY = "reply";

    public static ResolveInfo findBestResolveInfo(List<ResolveInfo> list) {
        if (null == list || list.size() < 1) return null;
        ResolveInfo best = null;
        for (ResolveInfo info : list) {
            if (info != null && (best == null || best.priority > info.priority))
                best = info;
            Log.v(TAG, "info:" + info);
        }
        return best;
    }

    public Intent setExplicitBroadcastIntent(Intent intent) {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> list = pm.queryBroadcastReceivers(intent, 0);
        ResolveInfo info = findBestResolveInfo(list);
        if (null == info) {
            Log.v(TAG, "Not found broadcast receiver");
            return null;
        }
        ComponentName compName = intent.getComponent();
        if (null != compName) {
            Log.v(TAG, "compName already set:" + compName);
            return intent;
        }
        ComponentName comp = new ComponentName(
                info.activityInfo.packageName, info.activityInfo.name);
        intent.setComponent(comp);
        return intent;
    }

    public boolean isBroadcastReceiverIntent(Intent intent) {
        PackageManager pkgManager = getPackageManager();
        List<ResolveInfo> list = pkgManager.queryBroadcastReceivers(intent, 0);
        if (null != list && list.size() >= 1) {
            return true;
        }
        Log.v(TAG, "Not found BroadcastReceiver");
        return false;
    }

    public boolean sendVideocallReplyBroadcast(String action, Bundle reply) {
        Intent intent = new Intent(ACTION_VIDEOCALL_FLOATING_WINDOW_REPLY);
        intent.putExtras(reply);
        //intent.putExtra(EXTRA_VIDEOCALL_FLOATING_WINDOW_REPLY, reply);
        if (isBroadcastReceiverIntent(intent)) {
            setExplicitBroadcastIntent(intent);
            Log.d(TAG, "sendVideocallReplyBroadcast:" + intent);
            sendBroadcast(intent);
            return true;
        }
        return false;
    }

    private void onVSMFloatingWindowProcess(Intent intent) {
        // #1
        String version = intent.getStringExtra("version");
        Log.d(TAG, " version:" + version);
        if (!"1.0".equals(version)) {
            Log.e(TAG, "invalid version " + version);
        }
        // #2
        String actionName = intent.getStringExtra("actionName");
        Log.d(TAG, " actionName:" + actionName); // pkgName
        // add a necessary extra
        String type = intent.getStringExtra("type");
        Log.d(TAG, " floating window type:" + type);
        Bundle hintReply = null, hint2Reply = null;
        switch (type) {
            case TYPE_VIDEOCALL_CALLING:
                hintReply = intent.getBundleExtra(EXTRA_VIDEOCALL_CANCEL);
                Log.d(TAG, "cancel:" + hintReply);
                break;
            case TYPE_VIDEOCALL_INCOMING:
                hintReply = intent.getBundleExtra(EXTRA_VIDEOCALL_PICKUP);
                Log.d(TAG, "pickup:" + hintReply);
                hint2Reply = intent.getBundleExtra(EXTRA_VIDEOCALL_HANGHUP);
                Log.d(TAG, "hangup:" + hint2Reply);
                break;
            case TYPE_CLOSE:
                // no more extras
                Log.d(TAG, "close window");
                handler.post(runnerForRemovingMsg);
                return;
            default:
                // add more extras below
                break;
        }
        // #3
        String titleString = intent.getStringExtra("title");
        Log.d(TAG, " title:" + titleString);
        // #4
        String content = intent.getStringExtra("content");
        Log.d(TAG, " content:" + content);
        String[] contentArgs = content.split("@");
        String contentString = "No content";
        String imgUrl = null;
        if (contentArgs.length >= 1 && contentArgs.length <= 2) {
            contentString = contentArgs[0];
            imgUrl = (contentArgs.length >= 2) ? contentArgs[1] : null;
            Log.d(TAG, "content:" + contentString + " url:" + imgUrl);
        } else {
            Log.e(TAG, "invalid content arg length " + contentArgs.length);
        }
        String hintString = "確認";
        int hintKeycode = KeyEvent.KEYCODE_PROG_GREEN;
        if (type.equals(TYPE_VIDEOCALL_CALLING)) {
            Log.d(TAG, " setup hint for making a call");
            hintString = "取消";
            hintKeycode = KeyEvent.KEYCODE_PROG_RED;
        } else if (type.equals(TYPE_VIDEOCALL_INCOMING)) {
            Log.d(TAG, " setup hint for incoming call");
            hintString = "接起";
            hintKeycode = KeyEvent.KEYCODE_PROG_GREEN;
        }
        // #5-2
        String hint2String = null;
        int hint2Keycode = -1;
        if (type.equals(TYPE_VIDEOCALL_INCOMING)) {
            Log.d(TAG, " setup hint2 for incoming call");
            hint2String = "掛斷";
            hint2Keycode = KeyEvent.KEYCODE_PROG_RED;
        }
        // #6
        boolean openApp = intent.getBooleanExtra("openApp", false);
        Log.d(TAG, " openApp:" + openApp);
        // #7
        int timeout = intent.getIntExtra("timeout", DEFAULT_TIMEOUT);
        Log.d(TAG, "timeout:" + timeout);
        addOverlayCustomizedView(createVSMFWOverlayView(
                actionName, type,
                titleString, contentString, imgUrl,
                hintString, hintKeycode, hintReply,
                hint2String, hint2Keycode, hint2Reply));
        handler.postDelayed(runnerForRemovingMsg, timeout);
    }

    private View createVSMFWOverlayView(
            String pkgName, final String type,
            String title, String content, String imgUrl,
            String hint, final int hintKey, final Bundle hintReply,
            String hint2, final int hint2Key, final Bundle hint2Reply) {
        View view;
        view = layoutInflater.inflate(R.layout.vsm_floating_window, new RelativeLayout(this) {
            final String windowtype = type;
            final int keycode = hintKey;
            final int keycode2 = hint2Key;
            final Bundle reply = hintReply;
            final Bundle reply2 = hint2Reply;
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getKeyCode() == keycode) {
                        Log.v(TAG, "keycoede:" + keycode + " pressed");
                        if (null != hintReply) {
                            if (MainService.this.sendVideocallReplyBroadcast(
                                    TYPE_VIDEOCALL_CALLING.equals(windowtype)?
                                            EXTRA_VIDEOCALL_PICKUP:EXTRA_VIDEOCALL_CANCEL,
                                    hintReply)) {
                                Toast.makeText(getApplicationContext(), "send reply", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getApplicationContext(), "ERROR:cannot send reply", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Log.v(TAG, "no reply found");
                        }
                        onRemoveMessage();
                        return true;
                    }
                    if (event.getKeyCode() == keycode2) {
                        Log.v(TAG, "keycode2:" + keycode2 + " pressed");
                        if (null != hint2Reply) {
                            if (MainService.this.sendVideocallReplyBroadcast(EXTRA_VIDEOCALL_HANGHUP, hint2Reply)) {
                                Toast.makeText(getApplicationContext(), "send reply2", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getApplicationContext(), "ERROR:cannot send reply2", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Log.v(TAG, "no reply found");
                        }
                        onRemoveMessage();
                        return true;
                    }
                }
                boolean ret = super.dispatchKeyEvent(event);
                /* test code beg */
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    Log.v(TAG, "key:" + event + " ret:" + ret);
                }
                /* test code end */
                return ret;
            }
        });
        boolean isEmergency = type.equals(TYPE_WARNING);
        Log.d(TAG, "emergency:" + isEmergency);
        if (isEmergency) {
            if (Build.VERSION.SDK_INT >= 23) {
                view.setBackgroundColor(getColor(R.color.colorEmergency));
            } else{
                view.setBackgroundColor(getResources().getColor(R.color.colorEmergency));
            }
        }
        TextView titleComp = view.findViewById(R.id.Title);
        if (TextUtils.isEmpty(title)) {
            titleComp.setVisibility(View.GONE);
        } else {
            titleComp.setVisibility(View.VISIBLE);
            Drawable titleDrawable = null;
            if (!TextUtils.isEmpty(pkgName)) {
                try {
                    // https://stackoverflow.com/questions/11961599/get-resource-id-of-the-icon-of-another-android-application
                    titleDrawable = getPackageManager().getApplicationIcon(pkgName);
                    Log.d(TAG, "get app icon drawable" + titleDrawable);
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e(TAG, "error get app icon:", e);
                }
            }
            if (null != titleDrawable) {
                titleDrawable.setBounds(0, 0, 36, 36);
                titleComp.setCompoundDrawables(titleDrawable, null, null, null);
                titleComp.setCompoundDrawablePadding(7);
            } else {
                titleComp.setCompoundDrawables(null, null, null, null);
            }
            titleComp.setText(title);
            if (isEmergency) {
                titleComp.setTextColor(getResources().getColor(R.color.colorEmergencyText));
            } else {
                titleComp.setTextColor(getResources().getColor(R.color.colorNormalText));
            }
            titleComp.setTypeface(titleComp.getTypeface(), Typeface.BOLD);
        }

        TextView contentComp = view.findViewById(R.id.Content);
        Log.d(TAG, "setText(content):" + content);
        contentComp.setText(content);
        if (isEmergency) {
            contentComp.setTextColor(getResources().getColor(R.color.colorEmergencyText));
            contentComp.setTypeface(contentComp.getTypeface(), Typeface.BOLD);
        } else {
            contentComp.setTextColor(getResources().getColor(R.color.colorNormalText));
            contentComp.setTypeface(contentComp.getTypeface(), Typeface.NORMAL);
        }

        ImageView imageComp = view.findViewById(R.id.Image);
        if (null != imgUrl) {
            Log.d(TAG, "setup image url:" + imgUrl);
            imageComp.setVisibility(View.VISIBLE);
            imageComp.setImageURI(Uri.parse(imgUrl));
        } else {
            imageComp.setVisibility(View.GONE);
        }

        TextView hintComp = view.findViewById(R.id.Hint1);
        hintComp.setText(hint);
        //hintComp.setClickable(true);
        hintComp.setOnClickListener(new View.OnClickListener() {
            final String windowtype = type;
            final Bundle reply = hintReply;
            final Bundle reply2 = hint2Reply;
            @Override
            public void onClick(View view) {
                if (null != hintReply) {
                    Log.d(TAG, "hint1 clicked");
                    if (MainService.this.sendVideocallReplyBroadcast(
                            TYPE_VIDEOCALL_CALLING.equals(windowtype)?
                                    EXTRA_VIDEOCALL_PICKUP:EXTRA_VIDEOCALL_CANCEL,
                            hintReply)) {
                        Toast.makeText(getApplicationContext(), "send reply", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getApplicationContext(), "ERROR:cannot send reply", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.v(TAG, "no reply found");
                }
                onRemoveMessage();
            }
        });
        if (isEmergency) {
            hintComp.setTextColor(getResources().getColor(R.color.colorEmergencyText));
            hintComp.setTypeface(hintComp.getTypeface(), Typeface.BOLD);
        } else {
            hintComp.setTextColor(getResources().getColor(R.color.colorNormalText));
            hintComp.setTypeface(hintComp.getTypeface(), Typeface.NORMAL);
        }
        Drawable hintDrawable = null;
        switch (hintKey) {
            case KeyEvent.KEYCODE_PROG_RED:
                hintDrawable = getResources().getDrawable(R.drawable.icon_r_30px);
                break;
            case KeyEvent.KEYCODE_PROG_GREEN:
                hintDrawable = getResources().getDrawable(R.drawable.icon_g_30px);
                break;
            case KeyEvent.KEYCODE_PROG_BLUE:
                hintDrawable = getResources().getDrawable(R.drawable.icon_b_36px);
                break;
            case KeyEvent.KEYCODE_PROG_YELLOW:
                //hintDrawable = getResources().getDrawable(R.drawable.icon_y_30px);
                break;
            default:
                Log.d(TAG, "error hint key:" + hintKey);
                break;
        }
        if (null != hintDrawable) {
            hintDrawable.setBounds(0, 0, 30, 30);
            hintComp.setCompoundDrawables(hintDrawable, null, null, null);
            hintComp.setCompoundDrawablePadding(7);
        } else {
            hintComp.setCompoundDrawables(null, null, null, null);
        }

        TextView hint2Comp = view.findViewById(R.id.Hint2);
        if (null == hint2) {
            hint2Comp.setVisibility(View.GONE);
        } else {
            hint2Comp.setVisibility(View.VISIBLE);
            hint2Comp.setText(hint);
            //hint2Comp.setClickable(true);
            hint2Comp.setOnClickListener(new View.OnClickListener() {
                final String windowtype = type;
                final Bundle reply2 = hint2Reply;
                @Override
                public void onClick(View view) {
                    if (null != hint2Reply) {
                        Log.d(TAG, "hint2 clicked");
                        if (MainService.this.sendVideocallReplyBroadcast(EXTRA_VIDEOCALL_HANGHUP, hint2Reply)) {
                            Toast.makeText(getApplicationContext(), "send reply2", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getApplicationContext(), "ERROR:cannot send reply2", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.v(TAG, "no reply found");
                    }
                    onRemoveMessage();
                }
            });
            if (isEmergency) {
                hint2Comp.setTextColor(getResources().getColor(R.color.colorEmergencyText));
                hint2Comp.setTypeface(hint2Comp.getTypeface(), Typeface.BOLD);
            } else {
                hint2Comp.setTextColor(getResources().getColor(R.color.colorNormalText));
                hint2Comp.setTypeface(hint2Comp.getTypeface(), Typeface.NORMAL);
            }
            Drawable hint2Drawable = null;
            switch (hint2Key) {
                case KeyEvent.KEYCODE_PROG_RED:
                    hint2Drawable = getResources().getDrawable(R.drawable.icon_r_30px);
                    break;
                case KeyEvent.KEYCODE_PROG_GREEN:
                    hint2Drawable = getResources().getDrawable(R.drawable.icon_g_30px);
                    break;
                case KeyEvent.KEYCODE_PROG_BLUE:
                    hint2Drawable = getResources().getDrawable(R.drawable.icon_b_36px);
                    break;
                case KeyEvent.KEYCODE_PROG_YELLOW:
                    //hint2Drawable = getResources().getDrawable(R.drawable.icon_y_30px);
                    break;
                default:
                    Log.d(TAG, "error hint2 key:" + hint2Key);
                    break;
            }
            if (null != hint2Drawable) {
                hint2Drawable.setBounds(0, 0, 30, 30);
                hint2Comp.setCompoundDrawables(hint2Drawable, null, null, null);
                hint2Comp.setCompoundDrawablePadding(7);
            } else {
                hint2Comp.setCompoundDrawables(null, null, null, null);
            }
        }

        LinearLayout window = view.findViewById(R.id.Window);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)window.getLayoutParams();
        if (!isEmergency) {
            Log.d(TAG, "RIGHTTOP");
            params.removeRule(RelativeLayout.CENTER_IN_PARENT);
            params.removeRule(RelativeLayout.ALIGN_PARENT_LEFT);
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
            params.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        } else {
            Log.d(TAG, "CENTER");
            params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
            params.removeRule(RelativeLayout.ALIGN_PARENT_LEFT);
            params.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
            params.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            params.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        }
        window.setLayoutParams(params);
        return view;
    }

    private RemoveMessageRunnable runnerForRemovingMsg = new RemoveMessageRunnable();
    class RemoveMessageRunnable implements Runnable {
        @Override
        public void run() {
            removeOverlayView();
            if (null != player) player.stop();
            //onRemoveMessage();
        }
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
        } else if (intent.hasExtra(EXTRA_PLAY_MOVIE_AND_CAMERA_PREVIEW_URL)) {
            msg = intent.getStringExtra(EXTRA_PLAY_MOVIE_AND_CAMERA_PREVIEW_URL);
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
            try {
                if (!checkSystemFeature())  {
                    Toast.makeText(getApplicationContext(), "no camera feature, cannot use camera", Toast.LENGTH_LONG).show();
                } else {
                    camera = getDefaultCamera();
                    if (camera == null) {
                        Toast.makeText(getApplicationContext(), "Cannot get camera", Toast.LENGTH_LONG).show();
                    } else {
                        enableCameraPreview(true);
                    }
                }
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), "Cannot get camera", Toast.LENGTH_LONG).show();
            }
            addOverlayView();
            return true;
        }
        return false;
    }

    private boolean checkSystemFeature() {
        boolean ret = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
        Log.v(TAG, "camera feature:" + ret);
        return ret;
    }

    private static Camera getDefaultCamera() {
        // Find the total number of cameras available
        int  numCamera = Camera.getNumberOfCameras();
        Log.v(TAG, "numCamera:" + numCamera);
        // Find the ID of the back-facing ("default") camera
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0; i < numCamera; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            Log.v(TAG, "cameraInfo:" + cameraInfo);
            return Camera.open(i);
        }
        return null;
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
        handler.removeCallbacks(runnerForRemovingMsg);
        handler.post(runnerForRemovingMsg);
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
        SurfaceView camView = view.findViewById(R.id.CameraOnFloaty);
        if (enableCameraPreview) {
            Log.v(TAG, "Enable Camera Preview");
            camView.setVisibility(View.VISIBLE);
            camHolder = camView.getHolder();
            camHolder.addCallback(camHolderCallback);
            enableCameraPreview = false; // one time
        } else {
            camView.setVisibility(View.GONE);
        }
        return view;
    }

    private void addOverlayCustomizedView(View view) {
        if (null == view) return;
        synchronized (this) {
            // already set view
            if (null != floatyView) {
                Log.d(TAG, "[AOCV] has a view on screen, remove it");
                removeOverlayView();
            }
            Log.v(TAG, "[AOCV] wm.addView");
            windowManager.addView(floatyView = view, wmLayoutParam);
        }
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
                if (null != floatyView) windowManager.removeView(floatyView);
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
