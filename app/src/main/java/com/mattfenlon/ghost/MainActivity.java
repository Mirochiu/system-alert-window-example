package com.mattfenlon.ghost;


import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    final static String TAG = "ALERT-TEST";

    public final static int PERMISSIONS_REQUEST_CODE1 = 10101;
    public final static int PERMISSIONS_REQUEST_CODE2 = 10102;

    private static final String[] PERMISSIONS_LIST = new String[]{
            Manifest.permission.CAMERA
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!checkPermStage1()) {
            requestPermStage1();
            return;
        }
        if (!checkPermStage2()) {
            requestPermStage2();
            return;
        }
        launchMainService();
    }

    private boolean checkPermStage1() {
        boolean allowAll = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<Integer> resList = new ArrayList();
            for (String perm : PERMISSIONS_LIST) {
                resList.add(checkSelfPermission(perm));
            }
            for (int res : resList) {
                if (PackageManager.PERMISSION_GRANTED != res) {
                    allowAll = false;
                    break;
                }
            }
            Log.v(TAG, "[CPS1] has all danger permission?" + allowAll);
        } else {
            Log.i(TAG, "[CPS1] no danger permission");
        }
        return allowAll;
    }

    private void requestPermStage1() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(PERMISSIONS_LIST, PERMISSIONS_REQUEST_CODE1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CODE1: {
                boolean allowAll = true;
                if (grantResults.length <= 0) return;
                for (int res : grantResults) {
                    if (PackageManager.PERMISSION_GRANTED != res) {
                        allowAll = false;
                        break;
                    }
                }
                if (!allowAll) {
                    showMsg("User now allowed to get all permission");
                } else {
                    if (!checkPermStage2()) {
                        requestPermStage2();
                    } else {
                        launchMainService();
                    }
                }
            }
        }
    }

    private boolean checkPermStage2() {
        boolean allowAll = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            allowAll = Settings.canDrawOverlays(getApplicationContext());
            Log.i(TAG, "[CPS2] has draw overlays");
        } else {
            Log.i(TAG, "[CPS2] no need overlays");
        }
        return allowAll;
    }

    public static boolean isActivityIntent(Context context, Intent intent) {
        PackageManager pkgManager = context.getPackageManager();
        List<ResolveInfo> list = pkgManager.queryIntentActivities(intent, 0);
        if (null != list && list.size() >= 1) {
            return true;
        }
        Log.v(TAG, "Not found Activity");
        return false;
    }

    private void requestPermStage2() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Log.i(TAG, "[RPS2] request overlay permission");
                // If not, form up an Intent to launch the permission request
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                // Workaround: put extras for the key event not handled in Overlay Setting page.
                intent.putExtra("extra_prefs_show_button_bar", true); // show the status bar
                intent.putExtra("extra_prefs_show_skip", false); // skip button is gone
                intent.putExtra("extra_prefs_set_next_text", ""); // next button is gone
                intent.putExtra("extra_prefs_set_back_text", getString(R.string.txtCancel));
                // Launch Intent, with the supplied request code
                if (isActivityIntent(getApplicationContext(), intent)) {
                    startActivityForResult(intent, PERMISSIONS_REQUEST_CODE2);
                } else {
                    showMsg("ERROR:Cannot found overlay permission request activity");
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,  Intent data) {
        Log.i(TAG, "[OAR]reqcode" + requestCode + " rescode=" + resultCode + " data=" + data);
        // Check if a request code is received that matches that which we provided for the overlay draw request
        if (PERMISSIONS_REQUEST_CODE2 == requestCode) {
            boolean res = checkPermStage2();
            // Double-check that the user granted it, and didn't just dismiss the request
            Log.i(TAG, "[OAR]canDrawOverlays=" + res);
            if (res) {
                Log.i(TAG, "[OAR]canDrawOverlays");
                // Launch the service
                launchMainService();
            }
            else {
                showMsg("Sorry. Can't draw overlays without permission...");
            }
        }
    }

    private void launchMainService() {
        // test adb commands
        //am startservice -e "show-message" "my message" com.mattfenlon.ghost/.MainService
        //am startservice -e "show-message-and-ringtone" "ringtone message" com.mattfenlon.ghost/.MainService
        //am startservice -e "show-message-and-music" "music message" com.mattfenlon.ghost/.MainService
        //am startservice -e "clear-message" "" com.mattfenlon.ghost/.MainService
        //am startservice -e "show-message-and-movie" "my message" com.mattfenlon.ghost/.MainService
        //am startservice -e "play-movie-url" "<null>" com.mattfenlon.ghost/.MainService
        //am startservice -e "play-music-url" "https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/data/sounds/Alarm_Classic.wav" com.mattfenlon.ghost/.MainService
        //am startservice -e "play-music-url" "<null>" com.mattfenlon.ghost/.MainService
        //am startservice -e "play-movie-url" "https://github.com/aosp-mirror/platform_frameworks_base/blob/master/data/videos/Disco.480p.mq.mp4?raw=true" com.mattfenlon.ghost/.MainService
        //am startservice -e "play-movie-url" "udp://230.1.2.108:11111/" com.mattfenlon.ghost/.MainService
        Log.i(TAG, "[LMS]startService");
        Intent service = new Intent(this, MainService.class);
        //service.putExtra("show-message", "launchMainService " + svc.hashCode());
        //service.putExtra("play-movie-url", "<null>");
        //service.putExtra("play-music-url", "<null>");
        //service.putExtra("show-message-and-ringtone", "ringtone message");
        //service.putExtra("play-movie-url", "udp://230.1.2.108:11111/");
        service.putExtra("play-movie-url-and-camera-preview", "<null>");
        ContextCompat.startForegroundService(getApplicationContext(), service);
        /*
        // https://stackoverflow.com/questions/46445265/android-8-0-java-lang-illegalstateexception-not-allowed-to-start-service-inten
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }
        */
        Log.i(TAG, "[LMS]finish");
        finish();
    }

    private void showMsg(final String msg) {
        try {
            if (!TextUtils.isEmpty(msg)) {
                Log.i(TAG, msg);
                Toast.makeText(getBaseContext(), msg, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            // nothing
        }
    }
}
