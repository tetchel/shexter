package ca.tetchel.shexter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Receives an Intent when the phone finishes booting which triggers starting the SmsServerService.
 */
public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if ("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {
            Intent pushIntent = new Intent(context, SmsServerService.class);
            context.startService(pushIntent);
        }
    }
}
