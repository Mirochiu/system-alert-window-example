package com.mattfenlon.ghost;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
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

import java.util.List;

public class MainService extends Service implements View.OnTouchListener {
    private static String TAG = MainService.class.getSimpleName();

    public static String ACTION_FLOATING_WINDOWS = "tw.com.chttl.FloatingWindows.message";

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
    private HandlerThread handlerThread;
    private Handler handler;

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

    final static int DEFAULT_BACKGROUND_COLOR = Color.WHITE;
    final static int DEFAULT_FOREGROUND_COLOR = Color.BLACK;
    final static int DEFAULT_KEYCODE = KeyEvent.KEYCODE_DPAD_CENTER;
    final static int DEFAULT_POSITION = 3;
    final static int DEFAULT_TIMEOUT = 1000;

    // additional extras for videocall
    public final static String EXTRA_VIDEOCALL_HANGHUP = "videocall.reply.hangup";
    public final static String EXTRA_VIDEOCALL_PICKUP = "videocall.reply.pickup";
    public final static String EXTRA_VIDEOCALL_CANCEL = "videocall.reply.cancel";

    public final static int TYPE_NORMAL = 0; // 一般訊息
    public final static int TYPE_EMERGENCY = 1; // 緊急訊息
    public final static int TYPE_VIDEOCALL_CALLING = 5001; // 視訊撥出訊息
    public final static int TYPE_VIDEOCALL_INCOMING = 5002; // 視訊撥入訊息
    public final static int TYPE_VIDEOCALL_INTERRUPTED = 5003; // 中斷/關閉視訊訊息

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

    public boolean sendVideocallReplyBroadcast(String reply) {
        Intent intent = new Intent(ACTION_VIDEOCALL_FLOATING_WINDOW_REPLY);
        intent.putExtra(EXTRA_VIDEOCALL_FLOATING_WINDOW_REPLY, reply);
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
        int type = intent.getIntExtra("type", TYPE_NORMAL);
        Log.d(TAG, " floating window type:" + type);
        String hintReply = null, hint2Reply = null, hint3Reply = null;
        switch (type) {
            case TYPE_VIDEOCALL_CALLING:
                hintReply = intent.getStringExtra(EXTRA_VIDEOCALL_CANCEL);
                Log.d(TAG, "cancel:" + hintReply);
                break;
            case TYPE_VIDEOCALL_INCOMING:
                hintReply = intent.getStringExtra(EXTRA_VIDEOCALL_PICKUP);
                Log.d(TAG, "pickup:" + hintReply);
                hint2Reply = intent.getStringExtra(EXTRA_VIDEOCALL_HANGHUP);
                Log.d(TAG, "hangup:" + hint2Reply);
                break;
            case TYPE_VIDEOCALL_INTERRUPTED:
                // no more extras
                Log.d(TAG, "videocall interrupted");
                handler.post(runnerForRemovingMsg);
                return;
            default:
                // add more extras below
                break;
        }
        // #3
        String title = intent.getStringExtra("title");
        Log.d(TAG, " title:" + title);
        String[] titleArgs = title.split("@");
        String titleString = "No title";
        int titleBgcolor = DEFAULT_BACKGROUND_COLOR;
        int titleFgcolor = DEFAULT_FOREGROUND_COLOR;
        if (titleArgs.length >= 1 && titleArgs.length <= 3) {
            titleString = titleArgs[0];
            titleBgcolor = (titleArgs.length >= 2) ? Color.parseColor("#" + titleArgs[1]) : DEFAULT_BACKGROUND_COLOR;
            titleFgcolor = (titleArgs.length >= 3) ? Color.parseColor("#" + titleArgs[2]) : DEFAULT_FOREGROUND_COLOR;
            Log.d(TAG, "title:" + titleString + " bg:" + titleBgcolor + " fg:" + titleFgcolor);
        } else {
            Log.e(TAG, "invalid title arg length " + titleArgs.length);
        }
        // #4
        String content = intent.getStringExtra("content");
        Log.d(TAG, " content:" + content);
        String[] contentArgs = content.split("@");
        String contentString = "No content";
        int contentBgcolor = DEFAULT_BACKGROUND_COLOR;
        int contentFgcolor = DEFAULT_FOREGROUND_COLOR;
        String imgUrl = null;
        if (contentArgs.length >= 1 && contentArgs.length <= 4) {
            contentString = contentArgs[0];
            contentBgcolor = (contentArgs.length >= 2) ? Color.parseColor("#" + contentArgs[1]) : DEFAULT_BACKGROUND_COLOR;
            contentFgcolor = (contentArgs.length >= 3) ? Color.parseColor("#" + contentArgs[2]) : DEFAULT_FOREGROUND_COLOR;
            imgUrl = (contentArgs.length >= 4) ? contentArgs[3] : null;
            Log.d(TAG, "content:" + contentString + " bg:" + contentBgcolor + " fg:" + contentFgcolor + " url:" + imgUrl);
        } else {
            Log.e(TAG, "invalid content arg length " + contentArgs.length);
        }
        // #5
        String hint = intent.getStringExtra("hint");
        Log.d(TAG, " hint:" + hint);
        String[] hint_args = hint.split("@");
        String hintString = "hint";
        int hintKeycode = DEFAULT_KEYCODE;
        if (type == TYPE_VIDEOCALL_CALLING) {
            Log.d(TAG, " setup hint for making a call");
            hintString = "取消";
            hintKeycode = KeyEvent.KEYCODE_PROG_RED;
        } else if (type == TYPE_VIDEOCALL_INCOMING) {
            Log.d(TAG, " setup hint for incoming call");
            hintString = "接起";
            hintKeycode = KeyEvent.KEYCODE_PROG_GREEN;
        } else if (hint_args.length >= 1 && hint_args.length <= 2) {
            hintString = hint_args[0];
            switch (hint_args[1]) {
                case "RED":
                    hintKeycode = KeyEvent.KEYCODE_PROG_RED;
                    break;
                case "GREEN":
                    hintKeycode = KeyEvent.KEYCODE_PROG_GREEN;
                    break;
                case "BLUE":
                    hintKeycode = KeyEvent.KEYCODE_PROG_BLUE;
                    break;
                case "YELLOW":
                    hintKeycode = KeyEvent.KEYCODE_PROG_YELLOW;
                    break;
                default:
                    Log.e(TAG, "invalid hint keycode " + hint_args[1]);
                    break;
            }
            Log.d(TAG, "hint:" + hintString + " key:" + hintKeycode);
        } else {
            Log.e(TAG, "invalid hint arg length " + hint_args.length);
        }
        // #5-2
        String hint2 = intent.getStringExtra("hint2");
        String hint2String = null;
        int hint2Keycode = -1;
        if (type == TYPE_VIDEOCALL_CALLING) {
            // no hint2
        } else if (type == TYPE_VIDEOCALL_INCOMING) {
            Log.d(TAG, " setup hint2 for incoming call");
            hint2String = "掛斷";
            hint2Keycode = KeyEvent.KEYCODE_PROG_RED;
        } else if (!TextUtils.isEmpty(hint2)) {
            Log.d(TAG, " hint2:" + hint2);
            String[] hint2_args = hint2.split("@");
            hint2String = "hint2";
            if (hint2_args.length >= 1 && hint2_args.length <= 2) {
                hint2String = hint2_args[0];
                switch (hint2_args[1]) {
                    case "RED": hintKeycode = KeyEvent.KEYCODE_PROG_RED; break;
                    case "GREEN": hintKeycode = KeyEvent.KEYCODE_PROG_GREEN; break;
                    case "BLUE": hintKeycode = KeyEvent.KEYCODE_PROG_BLUE; break;
                    case "YELLOW": hint2Keycode = KeyEvent.KEYCODE_PROG_YELLOW; break;
                    default:
                        Log.e(TAG, "invalid hint2 keycode " + hint2_args[1]);
                        break;
                }
                Log.d(TAG, "hint2:" + hint2String + " key:" + hint2Keycode);
            } else {
                Log.e(TAG, "invalid hint2 arg length " + hint2_args.length);
            }
        }
        // #5-3
        String hint3 = intent.getStringExtra("hint3");
        String hint3String = null;
        int hint3Keycode = -1;
        if (type == TYPE_VIDEOCALL_CALLING) {
            // no hint3
        } else if (type == TYPE_VIDEOCALL_INCOMING) {
            // no hint3
        } else if (!TextUtils.isEmpty(hint3)) {
            Log.d(TAG, " hint3:" + hint3);
            String[] hint3_args = hint3.split("@");
            hint3String = "hint3";
            if (hint3_args.length >= 1 && hint3_args.length <= 2) {
                hint3String = hint3_args[0];
                switch (hint3_args[1]) {
                    case "RED": hint3Keycode = KeyEvent.KEYCODE_PROG_RED; break;
                    case "GREEN": hint3Keycode = KeyEvent.KEYCODE_PROG_GREEN; break;
                    case "BLUE": hint3Keycode = KeyEvent.KEYCODE_PROG_BLUE; break;
                    case "YELLOW": hint3Keycode = KeyEvent.KEYCODE_PROG_YELLOW; break;
                    default:
                        Log.e(TAG, "invalid hint3 keycode " + hint3_args[1]);
                        break;
                }
                Log.d(TAG, "hint3:" + hint3String + " key:" + hint3Keycode);
            } else {
                Log.e(TAG, "invalid hint3 arg length " + hint3_args.length);
            }
        }
        // #6
        int position = intent.getIntExtra("position", DEFAULT_POSITION);
        if (type == TYPE_VIDEOCALL_CALLING) {
            Log.d(TAG, " setup position for making a call");
            position = 3; // right top
        } else if (type == TYPE_VIDEOCALL_INCOMING) {
            Log.d(TAG, " setup position for incoming call");
            position = 3; // right top
        }
        Log.d(TAG, "position:" + position);
        // #7
        int timeout = intent.getIntExtra("timeout", DEFAULT_TIMEOUT);
        Log.d(TAG, "timeout:" + timeout);
        addOverlayCustomizedView(createVSMFWOverlayView(
                actionName, type,
                titleString, titleBgcolor, titleFgcolor,
                contentString, contentBgcolor, contentFgcolor, imgUrl,
                hintString, hintKeycode, hintReply,
                hint2String, hint2Keycode, hint2Reply,
                hint3String, hint3Keycode, hint3Reply,
                position));
        handler.postDelayed(runnerForRemovingMsg, timeout);
    }

    private View createVSMFWOverlayView(
            String pkgName, int type,
            String title, int titleBgcolor, int titleFgcolor,
            String content, int contentBgcolor, int contentFgcolor, String imgUrl,
            String hint, final int hintKey, final String hintReply,
            String hint2, final int hint2Key, final String hint2Reply,
            String hint3, final int hint3Key, final String hint3Reply,
            int position) {
        View view;
        view = layoutInflater.inflate(R.layout.vsm_floating_window, new RelativeLayout(this) {
            final int keycode = hintKey;
            final int keycode2 = hint2Key;
            final int keycode3 = hint3Key;
            final String reply = hintReply;
            final String reply2 = hint2Reply;
            final String reply3 = hint3Reply;
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                Log.v(TAG, "dispatchTouchEvent " + ev);
                boolean ret = super.dispatchTouchEvent(ev);
                Log.d(TAG, "dispatchTouchEvent ret=" + ret + " " + ev);
                return ret;
            }
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getKeyCode() == keycode) {
                        Log.v(TAG, "keycoede:" + keycode + " pressed");
                        if (null != hintReply) {
                            if (MainService.this.sendVideocallReplyBroadcast(hintReply)) {
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
                            if (MainService.this.sendVideocallReplyBroadcast(hint2Reply)) {
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
                    if (event.getKeyCode() == keycode3) {
                        Log.v(TAG, "keycode3:" + keycode3 + " pressed");
                        if (null != hint3Reply) {
                            if (MainService.this.sendVideocallReplyBroadcast(hint3Reply)) {
                                Toast.makeText(getApplicationContext(), "send reply3", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getApplicationContext(), "ERROR:cannot send reply3", Toast.LENGTH_SHORT).show();
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
        boolean isEmergency = type == TYPE_EMERGENCY;
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
            //titleComp.setBackgroundColor(titleBgcolor);
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
        //contentComp.setBackgroundColor(contentBgcolor);
        contentComp.setTextColor(contentFgcolor);
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

        TextView hint3Comp = view.findViewById(R.id.Hint3);
        if (null == hint3) {
            hint3Comp.setVisibility(View.GONE);
        } else {
            hint3Comp.setVisibility(View.VISIBLE);
            hint3Comp.setText(hint);
            if (isEmergency) {
                hint3Comp.setTextColor(getResources().getColor(R.color.colorEmergencyText));
                hint3Comp.setTypeface(hint3Comp.getTypeface(), Typeface.BOLD);
            } else {
                hint3Comp.setTextColor(getResources().getColor(R.color.colorNormalText));
                hint3Comp.setTypeface(hint3Comp.getTypeface(), Typeface.NORMAL);
            }
            Drawable hint3Drawable = null;
            switch (hint3Key) {
                case KeyEvent.KEYCODE_PROG_RED:
                    hint3Drawable = getResources().getDrawable(R.drawable.icon_r_30px);
                    break;
                case KeyEvent.KEYCODE_PROG_GREEN:
                    hint3Drawable = getResources().getDrawable(R.drawable.icon_g_30px);
                    break;
                case KeyEvent.KEYCODE_PROG_BLUE:
                    hint3Drawable = getResources().getDrawable(R.drawable.icon_b_36px);
                    break;
                case KeyEvent.KEYCODE_PROG_YELLOW:
                    //hint3Drawable = getResources().getDrawable(R.drawable.icon_y_30px);
                    break;
                default:
                    Log.d(TAG, "error hint3 key:" + hint2Key);
                    break;
            }
            if (null != hint3Drawable) {
                hint3Drawable.setBounds(0, 0, 30, 30);
                hint3Comp.setCompoundDrawables(hint3Drawable, null, null, null);
                hint3Comp.setCompoundDrawablePadding(7);
            } else {
                hint3Comp.setCompoundDrawables(null, null, null, null);
            }
        }

        LinearLayout window = view.findViewById(R.id.Window);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)window.getLayoutParams();
        switch (position) {
            case 0:
                Log.d(TAG, "CENTER");
                params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
                params.removeRule(RelativeLayout.ALIGN_PARENT_LEFT);
                params.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
                params.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                params.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                break;
            case 1:
                Log.d(TAG, "LEFTTOP");
                params.removeRule(RelativeLayout.CENTER_IN_PARENT);
                params.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
                params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
                params.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                params.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                break;
            case 2:
                Log.d(TAG, "LEFTBOTTOM");
                params.removeRule(RelativeLayout.CENTER_IN_PARENT);
                params.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
                params.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
                params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
                params.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                break;
            default:
            case 3:
                Log.d(TAG, "RIGHTTOP");
                params.removeRule(RelativeLayout.CENTER_IN_PARENT);
                params.removeRule(RelativeLayout.ALIGN_PARENT_LEFT);
                params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
                params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
                params.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                break;
            case 4:
                Log.d(TAG, "RIGHTBOTTOM");
                params.removeRule(RelativeLayout.CENTER_IN_PARENT);
                params.removeRule(RelativeLayout.ALIGN_PARENT_LEFT);
                params.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
                params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
                params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
                break;
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
