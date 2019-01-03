package com.mattfenlon.ghost;


import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    final static String TAG = "ALERT-TEST";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Settings.canDrawOverlays(this)) {
            Log.i(TAG, "[OC]draw overlays");
            // Launch service right away - the user has already previously granted permission
            launchMainService();
        }
        else {
            Log.i(TAG, "[OC]cannot draw overlays");
            // Check that the user has granted permission, and prompt them if not
            checkDrawOverlayPermission();
        }
    }

    private void launchMainService() {
        // test adb commands
        //am startservice -e "show-message" "my message" com.mattfenlon.ghost/.MainService
        //am startservice -e "show-message-and-rington" "rington message" com.mattfenlon.ghost/.MainService
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
        service.putExtra("play-movie-url", "<null>");
        //service.putExtra("play-music-url", "<null>");
        // https://stackoverflow.com/questions/46445265/android-8-0-java-lang-illegalstateexception-not-allowed-to-start-service-inten
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }
        Log.i(TAG, "[LMS]finish");
        finish();
    }

    public final static int REQUEST_CODE = 10101;

    public void checkDrawOverlayPermission() {
        // Checks if app already has permission to draw overlays
        if (!Settings.canDrawOverlays(this)) {
            Log.i(TAG, "[CDOP]request overlay permission");
            // If not, form up an Intent to launch the permission request
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            // Workaround: put extras for the key event not handled in Overlay Setting page.
            intent.putExtra("extra_prefs_show_button_bar", true); // show the status bar
            intent.putExtra("extra_prefs_show_skip", false); // skip button is gone
            intent.putExtra("extra_prefs_set_next_text", ""); // next button is gone
            intent.putExtra("extra_prefs_set_back_text", getString(R.string.txtCancel));
            // Launch Intent, with the supplied request code
            startActivityForResult(intent, REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,  Intent data) {
        Log.i(TAG, "[OAR]reqcode" + requestCode + " rescode=" + resultCode + " data=" + data);
        // Check if a request code is received that matches that which we provided for the overlay draw request
        if (requestCode == REQUEST_CODE) {
            boolean res = Settings.canDrawOverlays(this);
            // Double-check that the user granted it, and didn't just dismiss the request
            Log.i(TAG, "[OAR]canDrawOverlays=" + res);
            if (res) {
                Log.i(TAG, "[OAR]canDrawOverlays");
                // Launch the service
                launchMainService();
            }
            else {
                Toast.makeText(this, "Sorry. Can't draw overlays without permission...", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
