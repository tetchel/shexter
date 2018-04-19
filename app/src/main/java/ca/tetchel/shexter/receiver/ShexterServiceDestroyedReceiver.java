package ca.tetchel.shexter.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import ca.tetchel.shexter.sms.ShexterService;

/**
 * Receives an Intent when the shexter service is destroyed
 */
public class ShexterServiceDestroyedReceiver extends BroadcastReceiver {

    private static final String TAG = ShexterServiceDestroyedReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Received an intent: " + intent.getAction());
        if (ShexterService.ON_DESTROY_INTENTFILTER.equals(intent.getAction())) {
            Intent serviceIntent = new Intent(context, ShexterService.class);
            Log.d(TAG, "restarting service");

            Toast.makeText(context, "shexter restarted", Toast.LENGTH_LONG).show();

            context.startService(serviceIntent);
        }
    }
}