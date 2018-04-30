package ca.tetchel.shexter.sms.util;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telephony.SmsMessage;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import ca.tetchel.shexter.R;
import ca.tetchel.shexter.RingCommandActivity;
import ca.tetchel.shexter.ShexterNotificationManager;
import ca.tetchel.shexter.eventlogger.EventLogger;
import ca.tetchel.shexter.main.MainActivity;
import ca.tetchel.shexter.sms.ShexterService;
import ca.tetchel.shexter.sms.subservices.SmsSendThread;

import static ca.tetchel.shexter.sms.util.ServiceConstants.COMMAND_CONTACTS;
import static ca.tetchel.shexter.sms.util.ServiceConstants.COMMAND_READ;
import static ca.tetchel.shexter.sms.util.ServiceConstants.COMMAND_RING;
import static ca.tetchel.shexter.sms.util.ServiceConstants.COMMAND_SEND;
import static ca.tetchel.shexter.sms.util.ServiceConstants.COMMAND_SEND_INITIALIZER;
import static ca.tetchel.shexter.sms.util.ServiceConstants.COMMAND_SETPREF;
import static ca.tetchel.shexter.sms.util.ServiceConstants.COMMAND_SETPREF_LIST;
import static ca.tetchel.shexter.sms.util.ServiceConstants.COMMAND_UNREAD;
import static ca.tetchel.shexter.sms.util.ServiceConstants.SETPREF_REQUIRED;

public class CommandProcessor {

    private static final String
            TAG = MainActivity.MASTER_TAG + CommandProcessor.class.getSimpleName();

    /**
     * @return The response to be returned to the client.
     */
    public static String process(Context context, String command, String originalRequest,
                                 Contact contact, BufferedReader requestReader)
            throws IOException {

        if (COMMAND_SEND_INITIALIZER.equals((command))) {
            // This command returns the name and number of the contact who will receive the message.
            if (contact == null) {
                return "No matching contact was found";
            }
            else {
                return contact.name() + ", " + contact.preferred();
            }
        }
        else if (COMMAND_SEND.equals(command)) {
            return sendCommand(context, contact, requestReader);
        }
        else if (COMMAND_READ.equals(command)) {
            return readCommand(context, contact, requestReader);
        }
        else if (COMMAND_UNREAD.equals(command)) {
            // Unread for a particular contact would need to be passed here
            return unreadCommand(context, requestReader);
        }
        else if (COMMAND_SETPREF_LIST.equals(command)) {
            Log.d(TAG, "SetPrefList");
            // respond to client with list of numbers to select from.
            StringBuilder listBuilder = new StringBuilder();
            listBuilder.append(SETPREF_REQUIRED).append('\n');
            listBuilder.append(contact.name())
                    .append(" has ")
                    .append(contact.count())
                    .append(" numbers: ");

            for (int i = 0; i < contact.count(); i++) {
                listBuilder.append('\n')
                        .append(i + 1)
                        .append(": ")
                        .append(contact.numbers().get(i));
            }
            if (contact.hasPreferred()) {
                listBuilder.append("\nCurrent: ")
                        .append(contact.preferred());
            }
            EventLogger.log(context, context.getString(R.string.event_command_succeeded),
                    context.getString(R.string.event_setpref_list, contact.name()));
            return listBuilder.toString();
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
                String resp = context.getString(R.string.event_setpref, contact.name(), contact.preferred());
                EventLogger.log(context, context.getString(R.string.event_command_succeeded), resp);
                return resp;
            }
            else {
                Log.d(TAG, "SetPref success; now running original command.");
                // still need to perform the original command
                return process(context, originalCommand, "", contact, originalRequestReader);
            }
        }
        else if (COMMAND_CONTACTS.equals(command)) {
            Log.d(TAG, "Contacts command.");
            try {
                String allContacts = SmsUtilities.getAllContacts(ShexterService.instance()
                        .getApplicationContext());
                if (allContacts != null && !allContacts.isEmpty()) {
                    EventLogger.log(context, context.getString(R.string.event_contacts, allContacts.length()));
                    Log.d(TAG, "Retrieved contacts successfully.");
                    return allContacts;
                }
                else {
                    EventLogger.log(context, context.getString(R.string.event_contacts, 0));
                    Log.d(TAG, "Retrieved NO contacts!");
                    return "An error occurred getting contacts, or you have no contacts!";
                }
            } catch (SecurityException e) {
                Log.w(TAG, "No contacts permission for Contacts command!");
                EventLogger.logError(context, context.getString(R.string.event_command_failed), e);
                return "No Contacts permission! Open the app and give Contacts permission.";
            }
        }
        else if (COMMAND_RING.equals(command)) {
            Log.d(TAG, "Ring command");
            return ringCommand(context);
        }
        else {
            EventLogger.logError(context, context.getString(R.string.event_unknown_command, command));
            //should never happen
            return "'" + command + "' is a not a recognized command. " +
                    "Please report this issue on GitHub.";
        }
    }

    private static String sendCommand(Context context, Contact contact, BufferedReader requestReader)
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

            /*
            String preferred = "";
            if(!contact.name().equals(contact.preferred())) {
                // Don't display the preferred twice in the -n case.
                preferred = ", " + contact.preferred();
            }*/

            Log.d(TAG, "Send command (probably) succeeded.");
            String response = context.getString(R.string.event_sent_messages,
                    //numberSent, numberSent != 1 ? "s" : "", contact.name(), preferred);
                    numberSent, numberSent != 1 ? "s" : "", contact.name());

            EventLogger.log(context, context.getString(R.string.event_command_succeeded), response);

            return response;
        } catch (SecurityException e) {
            Log.w(TAG, "No SMS Permission for send!", e);
            EventLogger.logError(context, context.getString(R.string.event_command_failed), e);
            return "No Send SMS permission! Open the app and give SMS permission.";
        } catch (Exception e) {
            Log.e(TAG, "Exception from sendThread", e);
            EventLogger.logError(context, context.getString(R.string.event_command_failed), e);
            return "Unexpected exception in the SMS send thread; " +
                    "please report this issue on GitHub.";
        }
    }

    private static String readCommand(Context context, Contact contact, BufferedReader requestReader)
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

                EventLogger.log(context, context.getString(R.string.event_command_succeeded),
                        context.getString(R.string.event_read_messages, contact.name()));

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
            Log.w(TAG, "No Read SMS permission!", e);
            EventLogger.logError(context, context.getString(R.string.event_command_failed), e);
            return "No Read SMS permission! Open the app and give SMS permission.";
        }
    }

    private static String unreadCommand(Context context, BufferedReader requestReader)
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
                        EventLogger.logError(context, e);
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

            EventLogger.log(context, context.getString(R.string.event_command_succeeded),
                    context.getString(R.string.event_unread));

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
            EventLogger.logError(context, context.getString(R.string.event_command_failed), e);
            return "No Read SMS permission! Open the app and give SMS permission.";
        }
    }

    private static String ringCommand(Context context) {

        NotificationManager nm = ShexterNotificationManager.tryGetNotifManager(context);

        if(nm == null || (MainActivity.isDndPermissionRequired() && !nm.isNotificationPolicyAccessGranted())) {
            EventLogger.logError(context, context.getString(R.string.event_command_failed),
                    context.getString(R.string.event_no_ring_perm));
            return context.getString(R.string.phone_ringing_failure_response);
        }
        else {
            Intent ringIntent = new Intent(context, RingCommandActivity.class);
            context.startActivity(ringIntent);
            EventLogger.log(context, context.getString(R.string.event_command_succeeded),
                    context.getString(R.string.event_ring));

            return context.getString(R.string.phone_ringing_response);
        }
    }
}
