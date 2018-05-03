package ca.tetchel.shexter.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import ca.tetchel.shexter.eventlogger.EventLogger;
import ca.tetchel.shexter.main.MainActivity;
import ca.tetchel.shexter.sms.ShexterService;

/**
 * Receives an Intent when the shexter service is destroyed
 */
public class ServiceDestroyedReceiever extends BroadcastReceiver {

    private static final String
            TAG = MainActivity.MASTER_TAG + ServiceDestroyedReceiever.class.getSimpleName();

    public ServiceDestroyedReceiever() {
        Log.d(TAG, "Starting up");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Received an intent: " + intent.getAction());
        if (ShexterService.ON_DESTROY_INTENTFILTER.equals(intent.getAction()) &&
                !ShexterService.isRunning()) {

            Intent serviceIntent = new Intent(context, ShexterService.class);
            Log.d(TAG, "restarting service");
            EventLogger.log(context, "Restarted service");

            context.startService(serviceIntent);
        }
    }
}