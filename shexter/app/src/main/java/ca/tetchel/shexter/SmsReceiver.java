package ca.tetchel.shexter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Receives Intents containing new SMS's, and signals to SMSServerService that there are new msgs.
 */
public class SmsReceiver extends BroadcastReceiver {

    private List<SmsMessage> messages = new ArrayList<>();

    @Override
    public void onReceive(Context context, Intent intent) {
        SmsMessage[] msgs = null;

        if (Build.VERSION.SDK_INT >= 19) {
            msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        } else {
            //deprecated way :(
            Bundle extras = intent.getExtras();
            Object pdus[] = (Object[]) extras.get("pdus");
            if (pdus != null) {
                msgs = new SmsMessage[pdus.length];
                for(int i = 0; i < pdus.length; i++)
                    msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
            }
        }

        if(msgs != null)
            messages.addAll(Arrays.asList(msgs));

        if (msgs != null && msgs.length > -1) {
            Log.i("SmsReceiver", "Received from: " +
                    msgs[0].getOriginatingAddress());
        }
    }

    public List<SmsMessage> getAllSms() {
        //convert to string / otherwise better format and put the common (shared with Service)
        //in Utils
        return messages;
    }
}
