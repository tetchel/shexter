package ca.tetchel.shexter.sms.service;

import android.os.AsyncTask;
import android.telephony.SmsManager;
import android.util.Log;

import java.util.ArrayList;

/**
 * Accepts a message and a phone number, and sends the message. Returns number sent.
 */
public class SmsSendThread extends AsyncTask<String, Void, Integer> {
    private static final String TAG = SmsSendThread.class.getSimpleName();

    @Override
    protected Integer doInBackground(String... params) {
        //Log.d(TAG, "About to send " + params[1] + " to " + params[0]);
        SmsManager smsm = SmsManager.getDefault();
        ArrayList<String> divided = smsm.divideMessage(params[1]);
        Log.d(TAG, "Message divided into " + divided.size() + " parts.");
        // could wait for the message to _actually_ be sent using PendingIntents
        if(divided.size() > 1) {
            smsm.sendMultipartTextMessage(params[0], null, divided, null, null);
        }
        else {
            smsm.sendTextMessage(params[0], null, params[1], null, null);
        }

        Log.d(TAG, "Messages sent successfully, probably.");
        return divided.size();
    }
}
