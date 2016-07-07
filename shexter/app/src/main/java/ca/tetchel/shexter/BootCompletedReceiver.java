package ca.tetchel.shexter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

/**
 * Receives an Intent when the phone finishes booting which triggers starting the SmsServerService.
 * //TODO need a way to stop/start the service (from UI)
 */
public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if ("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {
            Intent pushIntent = new Intent(context, SmsServerService.class);
            Log.d("BootCompletedReceiver", "Starting SMSServer on boot.");
            context.startService(pushIntent);
        }
    }
}