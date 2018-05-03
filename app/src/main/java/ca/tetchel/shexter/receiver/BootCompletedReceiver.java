package ca.tetchel.shexter.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import ca.tetchel.shexter.eventlogger.EventLogger;
import ca.tetchel.shexter.main.MainActivity;
import ca.tetchel.shexter.sms.ShexterService;

/**
 * Receives an Intent when the phone finishes booting which triggers starting the ShexterService.
 */
public class BootCompletedReceiver extends BroadcastReceiver {

    private static final String TAG = MainActivity.MASTER_TAG +
            BootCompletedReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Received an intent: " + intent.getAction());
        if(!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        if(ShexterService.isRunning()) {
            Log.d(TAG, "Not starting on boot because it's already running somehow");
            return;
        }
        Intent pushIntent = new Intent(context, ShexterService.class);
        Log.d(TAG, "Starting ShexterService on boot.");
        EventLogger.log(context, "Started service on boot");

        context.startService(pushIntent);
    }
}