package com.mattfenlon.ghost;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

public class MyReceiver extends BroadcastReceiver {
    static String TAG = "VSMFW-Emulator";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null) {
            Log.i(TAG, "not supported usage #1");
            return;
        }
        if (intent == null || !MainService.ACTION_FLOATING_WINDOWS.equals(intent.getAction())) {
            Log.i(TAG, "not supported usage #2");
            return;
        }
        try {
            Intent service = new Intent(context, MainService.class);
            service.setAction(MainService.ACTION_FLOATING_WINDOWS);
            service.putExtras(intent);
            ContextCompat.startForegroundService(context, service);
        } catch (Exception e1) {
            try {
                Toast.makeText(context, "cannot launch floating windows service", Toast.LENGTH_LONG).show();
            } catch (Exception e2) {
                Log.d(TAG, "cannot show toast");
            }
            Log.d(TAG, "launching floating windows failed", e1);
        }
    }
}
