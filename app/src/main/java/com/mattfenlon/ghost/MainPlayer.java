package com.mattfenlon.ghost;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.Toast;

import java.io.FileDescriptor;

public class MainPlayer {
    private static String TAG = MainPlayer.class.getSimpleName();
    private static boolean DEBUG = true;

    static final int TYPE_MUSIC = 0;
    static final int TYPE_VIDEO = 1;
    static final Callback DEFAULT_CALLBACK = new Callback() {
        @Override
        public SurfaceHolder onDisplayRequired() {
            if (DEBUG) Log.d(TAG, "not found callback registered");
            return null;
        }
        @Override
        public void onError(MainPlayer player, int i, int i1) {
            if (DEBUG) Log.d(TAG, "not found callback registered");
        }
        @Override
        public void onStart(MainPlayer player) {
            if (DEBUG) Log.d(TAG, "not found callback registered");
        }
        @Override
        public void onCompletion(MainPlayer player) {
            if (DEBUG) Log.d(TAG, "not found callback registered");
        }
    };
    private Callback callback;
    private Context context;
    private Object mpLock = new Object();
    private MediaPlayer mp;
    private HandlerThread handlerThread;
    private Handler handler;

    public interface Callback {
        SurfaceHolder onDisplayRequired();
        void onError(MainPlayer player, int i1, int i2);
        void onStart(MainPlayer player);
        void onCompletion(MainPlayer player);
    }

    public MainPlayer(Context context, Callback callback) {
        if (DEBUG) Log.v(TAG, "in MainPlayer");
        this.context = context;
        if (null == callback) callback = DEFAULT_CALLBACK;
        this.callback = callback;
        try {
            handlerThread = new HandlerThread("service-thread");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
        } catch (Exception e) {
            Log.e(TAG, "[OC] service thread error:" +e.getMessage());
        }
    }

    public void release() {
        if (DEBUG) Log.v(TAG, "in release");
        callback = DEFAULT_CALLBACK;
        stopPlayback();
        try {
            if (null != handler) {
                handler.removeCallbacksAndMessages(null);
            }
            if (null != handlerThread) {
                handlerThread.quitSafely();
            }
        } finally {
            handlerThread = null;
            handler = null;
        }
    }

    public void stopPlayback() {
        if (DEBUG) Log.v(TAG, "in stopPlayback");
        synchronized (mpLock) {
            try {
                if (null != mp) {
                    if (mp.isPlaying()) {
                        if (DEBUG) Log.v(TAG, "stop mp");
                        mp.stop();
                    }
                    if (DEBUG) Log.v(TAG, "release mp");
                    mp.release();
                }
            } finally {
                mp = null;
            }
        }
    }

    public void playURL(String url, int type) {
        startPlayback(url, type);
    }

    public void playAsset(String assetPath, int type) {
        try {
            AssetManager asset = context.getAssets();
            if (DEBUG) Log.v(TAG, "AssetManager=" + asset);
            // we cannot play music or video on my phone, but STB can
            //FileDescriptor fd = asset.openFd(assetPath).getFileDescriptor();
            //Log.v(TAG, "InputStream=" + fd);
            //startPlayback(fd, type);
            startPlayback(asset.openFd(assetPath), type);
        } catch (Exception e) {
            Log.e(TAG, "got exception", e);
            e.printStackTrace();
        }
    }

    protected void startPlayback(final Object mediaSource, final int type) {
        handler.post(new Runnable() {
            public void run() {
                if (DEBUG) Log.v(TAG, "in startPlayback " + mediaSource + " type" + type);
                try {
                    synchronized (mpLock) {
                        if (DEBUG)  Log.v(TAG, "new mp");
                        mp = new MediaPlayer();
                        if (mediaSource instanceof FileDescriptor) {
                            if (DEBUG) Log.v(TAG, "play from FileDescriptor");
                            mp.setDataSource((FileDescriptor) mediaSource);
                        } else if (mediaSource instanceof AssetFileDescriptor) {
                            // https://stackoverflow.com/questions/3289038/play-audio-file-from-the-assets-directory
                            if (DEBUG) Log.v(TAG, "play from AssetFileDescriptor");
                            AssetFileDescriptor afd = (AssetFileDescriptor) mediaSource;
                            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                        } else if (mediaSource instanceof String) {
                            if (DEBUG) Log.v(TAG, "play url");
                            mp.setDataSource((String) mediaSource);
                        }
                        mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                            @Override
                            public void onPrepared(MediaPlayer mediaPlayer) {
                                if (DEBUG) Log.v(TAG, "onPrepared");
                                if (TYPE_VIDEO == type) {
                                    SurfaceHolder display = callback.onDisplayRequired();
                                    if (null != display) {
                                        mp.setDisplay(display);
                                    } else {
                                        Log.e(TAG, "TYPE_VIDEO but onDisplayRequired is null");
                                    }
                                }
                                if (DEBUG) Log.v(TAG, "start play");
                                mp.start();
                                callback.onStart(MainPlayer.this);
                            }
                        });
                        mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                            @Override
                            public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
                                callback.onError(MainPlayer.this, i, i1);
                                return false;
                            }
                        });
                        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            @Override
                            public void onCompletion(MediaPlayer mediaPlayer) {
                                callback.onCompletion (MainPlayer.this);
                            }
                        });
                        if (DEBUG) Log.v(TAG, "prepareAsync");
                        mp.prepareAsync();
                    }
                    if (DEBUG) Log.v(TAG, "out startPlayback");
                } catch (Exception e) {
                    if (DEBUG) Log.v(TAG, "got exception");
                    callback.onError(MainPlayer.this, -1, 123456);
                }
            }
        });
    }
}
