package ca.tetchel.shexter.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Receives Intents containing new SMS's, and signals to SMSServerService that there are new msgs.
 */
public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "SmsReceiver";

    private static List<SmsMessage> messages = new ArrayList<>();

//    private static final Object messagesLock = new Object();

    @Override
    @SuppressWarnings("deprecated")
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

        /*
        if (msgs != null && msgs.length > -1) {
            Log.i(TAG, "Received from: " +
                    msgs[0].getOriginatingAddress());
        }
        */
//        Log.d(TAG, messages.size() + " messages are unread");
    }

    /**
     * Call this after a read command to clear the unread messages for that conversation.
     * @param number Number for which to remove unread messages.
     */
    public void removeMessagesFromNumber(String number) {
        for(Iterator<SmsMessage> it = messages.iterator(); it.hasNext();) {
            SmsMessage sms = it.next();
            if(PhoneNumberUtils.compare(sms.getOriginatingAddress(), number)) {
                it.remove();
            }
        }
    }

    public List<SmsMessage> getAllSms() {
        List<SmsMessage> messagesDupe = new ArrayList<>(messages);
        messages.clear();
        return messagesDupe;
    }
}
