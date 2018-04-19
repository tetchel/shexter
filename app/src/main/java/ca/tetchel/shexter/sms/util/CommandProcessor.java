package ca.tetchel.shexter.sms.util;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Vibrator;
import android.provider.ContactsContract;
import android.telephony.SmsMessage;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ca.tetchel.shexter.R;
import ca.tetchel.shexter.RingCommandActivity;
import ca.tetchel.shexter.sms.ShexterService;
import ca.tetchel.shexter.sms.subservices.SmsSendThread;

import static ca.tetchel.shexter.sms.util.ServiceConstants.COMMAND_CONTACTS;
import static ca.tetchel.shexter.sms.util.ServiceConstants.COMMAND_READ;
import static ca.tetchel.shexter.sms.util.ServiceConstants.COMMAND_RING;
import static ca.tetchel.shexter.sms.util.ServiceConstants.COMMAND_SEND;
import static ca.tetchel.shexter.sms.util.ServiceConstants.COMMAND_SETPREF;
import static ca.tetchel.shexter.sms.util.ServiceConstants.COMMAND_SETPREF_LIST;
import static ca.tetchel.shexter.sms.util.ServiceConstants.COMMAND_UNREAD;
import static ca.tetchel.shexter.sms.util.ServiceConstants.SETPREF_REQUIRED;

public class CommandProcessor {

    private static final String TAG = CommandProcessor.class.getSimpleName();

    /**
     * @return The response to be returned to the client.
     */
    public static String process(String command, String originalRequest, Contact contact,
                                    BufferedReader requestReader)
            throws IOException {
        if (COMMAND_SEND.equals(command)) {
            return sendCommand(contact, requestReader);
        }
        else if (COMMAND_READ.equals(command)) {
            return readCommand(contact, requestReader);
        }
        else if (COMMAND_UNREAD.equals(command)) {
            // Unread for a particular contact would need to be passed here
            return unreadCommand(requestReader);
        }
        else if (COMMAND_SETPREF_LIST.equals(command)) {
            Log.d(TAG, "SetPrefList");
            // respond to client with list of numbers to select from.
            String list = SETPREF_REQUIRED + '\n';
            list += contact.name() + " has " + contact.count() + " numbers: ";
            for (int i = 0; i < contact.count(); i++) {
                list += "\n" + (i + 1) + ": " + contact.numbers().get(i);
            }
            if (contact.hasPreferred()) {
                list += "\nCurrent: " + contact.preferred();
            }
            return list;
        }
        else if (COMMAND_SETPREF.equals(command)) {
            Log.d(TAG, "SetPrefCommand");
            // receive the index to set the new preferred number to.
            int replyIndex = Integer.parseInt(requestReader.readLine());
            Log.d(TAG, "Setting  pref to " + replyIndex);
            contact.setPreferred(replyIndex);

            // Get the original command's data - will do a recursive call to perform it.
            BufferedReader originalRequestReader = new BufferedReader(new StringReader(originalRequest));
            String originalCommand = originalRequestReader.readLine();
            // Remove the contact from the original - we already know it
            originalRequestReader.readLine();

            if(COMMAND_SETPREF.equals(originalCommand)) {
                Log.d(TAG, "Regular setpref success.");
                return "Changed " + contact.name() + "'s preferred number to: " +
                        contact.preferred();
            }
            else {
                Log.d(TAG, "SetPref success; now running original command.");
                // still need to perform the original command
                return process(originalCommand, "", contact, originalRequestReader);
            }
        }
        else if (COMMAND_CONTACTS.equals(command)) {
            Log.d(TAG, "Contacts command.");
            try {
                String allContacts = SmsUtilities.getAllContacts(ShexterService.instance()
                        .getContentResolver());
                if (allContacts != null && !allContacts.isEmpty()) {
                    Log.d(TAG, "Retrieved contacts successfully.");
                    return allContacts;
                }
                else {
                    Log.d(TAG, "Retrieved NO contacts!");
                    return "An error occurred getting contacts, or you have no contacts!";
                }
            } catch (SecurityException e) {
                Log.w(TAG, "No contacts permission for Contacts command!");
                return "No Contacts permission! Open the " + R.string.app_name +
                        " app and give Contacts permission.";
            }
        }
        else if (COMMAND_RING.equals(command)) {
            Log.d(TAG, "Ring command");
            return ringCommand();
        }
        else {
            //should never happen
            return "'" + command + "' is a not a recognized command. " +
                    "Please report this issue on GitHub.";
        }
    }

    private static String sendCommand(Contact contact, BufferedReader requestReader)
            throws IOException {
        Log.d(TAG, "SendCommand");
        StringBuilder msgBodyBuilder = new StringBuilder();
        //read the message body into msgBodyBuilder
        int current;
        char previous = ' ';
        while ((current = requestReader.read()) != -1) {
            char chr = (char) current;
            if (chr == '\n' && previous == '\n') {
                // Double newline means end-of-send.
                // It would be preferable for the request to contain a length header.
                break;
            }
            msgBodyBuilder.append(chr);
            previous = chr;
        }

        String messageInput = msgBodyBuilder.toString();

        try {
            Integer numberSent = (new SmsSendThread().execute(contact.preferred(), messageInput))
                    .get();

            String preferred = "";
            if(!contact.name().equals(contact.preferred())) {
                // Don't display the preferred twice in the -n case.
                preferred = ", " + contact.preferred();
            }

            Log.d(TAG, "Send command (probably) succeeded.");
            return String.format(Locale.getDefault(), "Successfully sent %d message%s to %s%s.",
                    numberSent, numberSent != 1 ? "s" : "", contact.name(), preferred);
        } catch (SecurityException e) {
            Log.w(TAG, "No SMS Permission for send!");
            return "No SMS permission! Open the " + R.string.app_name +
                    " app and give SMS permission.";
        } catch (Exception e) {
            Log.e(TAG, "Exception from sendThread", e);
            return "Unexpected exception in the SMS send thread; " +
                    "please report this issue on GitHub.";
        }
    }

    private static String readCommand(Contact contact, BufferedReader requestReader)
            throws IOException {
        Log.d(TAG, "Enter ReadCommand");
        int numberToRetrieve = Integer.parseInt(requestReader.readLine());
        int outputWidth = Integer.parseInt(requestReader.readLine());

        try {
            String convo = SmsUtilities.getConversation(ShexterService.instance()
                            .getContentResolver(),
                    contact, numberToRetrieve, outputWidth);

            if (convo != null) {
                ShexterService.instance().getSmsReceiver()
                        .removeMessagesFromNumber(contact.preferred());
                Log.d(TAG, "Responded with convo.");
                return convo;
            }
            else {
                String response = "No messages found with " + contact.name();
                if (!contact.name().equals(contact.preferred())) {
                    // Don't display the preferred twice in the -n case.
                    response += ", " + contact.preferred() + ".";
                }
                Log.d(TAG, "Responded with no convo.");
                return response + '.';
            }

        } catch (SecurityException e) {
            Log.w(TAG, "No SMS permission for reading.");
            return "No SMS permission! Open the " + R.string.app_name +
                    " app and give SMS permission.";
        }
    }

    private static String unreadCommand(BufferedReader requestReader)
            throws IOException {
        Log.d(TAG, "UnreadCommand");
        int outputWidth = Integer.parseInt(requestReader.readLine());
        try {
            List<SmsMessage> unreadMessages = ShexterService.instance().getSmsReceiver()
                    .getAllSms();

            List<String> formattedMessages = new ArrayList<>(unreadMessages.size());
            List<Long> dates = new ArrayList<>(unreadMessages.size());

            for(SmsMessage sms : unreadMessages) {
                String sender = sms.getOriginatingAddress();
                Uri lookupUri = Uri.withAppendedPath(
                        ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                        Uri.encode(sender));
                Cursor c = ShexterService.instance().getContentResolver()
                        .query(lookupUri, new String[]{ ContactsContract.Data.DISPLAY_NAME },
                                null, null, null);

                if(c != null) {
                    try {
                        if(c.moveToFirst()) {
                            String displayName = c.getString(0);
                            if(!displayName.isEmpty()) {
                                sender = displayName + ": ";
                            }
                        }
                    }
                    catch (Exception e) {
                        Log.e(TAG, "Exception occurred getting contact name for sms.", e);
                    }
                    finally {
                        c.close();
                    }
                }

                long timestamp = sms.getTimestampMillis();
                formattedMessages.add(SmsUtilities.formatSms(sender, "", sms.getMessageBody(),
                        timestamp, outputWidth));
                dates.add(timestamp);
            }

            String unread = SmsUtilities.messagesIntoOutput(formattedMessages, dates);

            if (!unread.isEmpty()) {
                unread = "Unread Messages:\n" + unread;
                Log.d(TAG, "Replying with unread messages.");
                return unread;
            }
            else {
                Log.d(TAG, "Replying with NO unread messages.");
                return "No unread messages.";
            }
        } catch (SecurityException e) {
            Log.w(TAG, "No SMS permission for reading.");
            return "Could not retrieve messages: make sure " +
                    R.string.app_name + " has SMS permission.";
        }
    }

    private static String ringCommand() {
        Context appContext = ShexterService.instance().getApplicationContext();

        // Initialize the ringtone before calling startPlaying
        Uri notifSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        Log.d(TAG, "Using ringtone at " + notifSound);
        Ringtone ringtone = RingtoneManager.getRingtone(ShexterService.instance().getApplicationContext(), notifSound);

        Vibrator vibrator = null;
        Object vibratorService = appContext.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibratorService != null) {
            vibrator = (Vibrator) vibratorService;
        }
        else {
            Log.e(TAG, "Couldn't get vibrator service!");
        }

        RingCommandActivity.ringtone = ringtone;
        RingCommandActivity.vibrator = vibrator;
        Intent ringIntent = new Intent(appContext, RingCommandActivity.class);
        appContext.startActivity(ringIntent);

        return "Phone's ringing!";
    }
}
