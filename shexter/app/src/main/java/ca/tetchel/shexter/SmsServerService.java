package ca.tetchel.shexter;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class SmsServerService extends Service {

    private final String TAG = "Shexter_SmsServer";

    private static final int PORT = 5678;

    private static final String COMMAND_SEND = "send",
                                COMMAND_READ = "read",
                                COMMAND_UNREAD = "unread",
                                UNREAD_CONTACT_FLAG = "-contact";

    private ServerSocket serverSocket;
    private ServerThread serverThread;

    private SmsReceiver receiver;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        serverThread = new ServerThread();
        serverThread.start();

        //TODO ask for permission

        //Register to receive new SMS intents
        receiver = new SmsReceiver();

        Log.d(TAG, getString(R.string.app_name) +" service started.");

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        serverThread.interrupt();
        try {
            if(serverSocket != null && !serverSocket.isClosed())
                serverSocket.close();
        }
        catch(IOException e) {
            Log.e(TAG, "Exception closing ServerSocket", e);
        }

        Log.d(TAG, getString(R.string.app_name) + " service stopped.");
    }

    private class ServerThread extends Thread {
        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(PORT);
            }
            catch(IOException e) {
                Log.e(TAG, "Exception opening ServerSocket", e);
                Toast.makeText(getApplicationContext(), "An exception occurred opening the server " +
                        "socket; the app will not work in its current state. Please try restarting " +
                        "the app and possibly your phone.", Toast.LENGTH_LONG).show();
                onDestroy();
            }
            while(!Thread.currentThread().isInterrupted() && !serverSocket.isClosed()) {
                try {
                    Log.d(TAG, "Ready to accept");
                    Socket socket = serverSocket.accept();
                    //reader from socket
                    BufferedReader in_reader = new BufferedReader(
                            new InputStreamReader(socket.getInputStream()));
                    //print back to socket
                    PrintStream replyStream = new PrintStream(socket.getOutputStream());

                    String errMsg = null;
                    //The request protocol is command\ncontact\nmessage,
                    //so read the first line to get the command
                    String command = in_reader.readLine();
                    //the user has the option to retrieve unread messages for specific contact
                    boolean unreadContactProvided =
                            (COMMAND_UNREAD + UNREAD_CONTACT_FLAG).equals(command);

                    String[] contactInfo = { "", "" };
                    if(COMMAND_READ.equals(command) || COMMAND_SEND.equals(command) ||
                            unreadContactProvided) {

                        //second line for contact name
                        String contact_name_input = in_reader.readLine();
                        //then get the contact's phone number from his/her name
                        try {
                            contactInfo = getContactInfo(contact_name_input);

                            if(contactInfo == null) {
                                //non existent contact
                                errMsg = "Couldn't find contact '" + contact_name_input +
                                        "', please make sure the contact exists and " +
                                        "is spelled correctly.";
                            }
                        }
                        catch(SecurityException e) {
                            errMsg = "Could not retrieve contact info: make sure " +
                                    getString(R.string.app_name) + " has Contacts permission.";
                        }
                    }
                    if(errMsg != null) {
                        //if something has gone wrong already, don't do anything else
                        sendReply(replyStream, errMsg);
                    }
                    else if(COMMAND_SEND.equals(command)) {
                        String line;
                        StringBuilder msgBodyBuilder = new StringBuilder();
                        //read the message body into msgBodyBuilder
                        while((line = in_reader.readLine()) != null) {
                            if(line.isEmpty())
                                break;
                            msgBodyBuilder.append(line).append('\n');
                        }

                        String messageInput = msgBodyBuilder.toString();
                        if(messageInput.isEmpty()) {
                            //this is already checked client-side, but just to be safe...
                            sendReply(replyStream, "Not sent: message body was empty.");
                            continue;
                        }

                        //chop off last newline
                        messageInput = messageInput.substring(0, messageInput.length()-1);
                        //Determine what encoding is required for this message.
                        int[] calcLength = SmsMessage.calculateLength(messageInput, false);

                        //now divide the message into submessages based on encoding type
                        int each_message_len;
                        if(calcLength[3] == SmsMessage.ENCODING_7BIT) {
                            each_message_len = SmsMessage.MAX_USER_DATA_SEPTETS;
                        }
                        else if(calcLength[3] == SmsMessage.ENCODING_8BIT) {
                            each_message_len = SmsMessage.MAX_USER_DATA_BYTES;
                        }
                        else if(calcLength[3] == SmsMessage.ENCODING_16BIT) {
                            each_message_len = SmsMessage.MAX_USER_DATA_BYTES / 2;
                        }
                        else {
                            //unknown encoding : error
                            sendReply(replyStream, "Unknown encoding in message to send. If you " +
                                    "are not sending characters that are outside UTF-16 and " +
                                    "believe this is an error, please report this on GitHub.");
                            each_message_len = -1;
                        }

                        List<String> messages;
                        if(each_message_len != -1) {
                            //TODO modify this code to use User Data Headers so that the receiver
                            //has the messages concatenated.
                            //https://en.wikipedia.org/wiki/Concatenated_SMS
                            messages = Utilities.divideString(messageInput, each_message_len);

                            SmsSendThread[] sendThreads = new SmsSendThread[messages.size()];
                            for(int i = 0; i < sendThreads.length; i++) {
                                sendThreads[i] = new SmsSendThread();
                            }
                            //attempt to send sms and handle errors appropriately
                            try {
                                Boolean result = true;
                                for (int i = 0; i < messages.size(); i++) {
                                    result = sendThreads[i].execute(contactInfo[0], messages.get(i))
                                            .get();     //.get gets the Boolean result from the task
                                    if(!result)
                                        break;
                                }
                                if (result) {
                                    sendReply(replyStream, "Successfully sent " + messages.size() +
                                            " message(s) using encoding #" + calcLength[3] + " to "+
                                            contactInfo[1] + ".");
                                } else {
                                    sendReply(replyStream, "Could not send message: make sure " +
                                            getString(R.string.app_name) + " has SMS permission.");
                                }
                            } catch (InterruptedException | ExecutionException e) {
                                sendReply(replyStream, "Unexpected exception in the SMS send " +
                                        "thread; please report this issue on GitHub.");
                                Log.e(TAG, "Exception from sendThread", e);
                            }
                        }
                    }
                    else if(COMMAND_READ.equals(command)) {
                        //TODO mark all messages in this conversation as 'read' (if possible)
                        int numberToRetrieve = Integer.parseInt(in_reader.readLine());
                        //Log.d(TAG, "" + numberToRetrieve);

                        String convo;
                        try {
                            convo = getConversation(contactInfo, numberToRetrieve);

                            if(convo != null)
                                sendReply(replyStream, convo);
                            else
                                sendReply(replyStream, "No messages found with " +
                                        contactInfo[1] + ".");

                        }
                        catch(SecurityException e) {
                            sendReply(replyStream, "Could not retrieve messages: make sure " +
                                    getString(R.string.app_name) + " has SMS permission.");
                            Log.w(TAG, "No SMS permission for reading.");
                        }
                    }
                    else if(unreadContactProvided || COMMAND_UNREAD.equals(command)) {
                        try {
                            int numberToRetrieve = Integer.parseInt(in_reader.readLine());
                            //List<String> rawConvo = getUnread(null)
                            String result = getUnread(contactInfo[0] ,numberToRetrieve);

                            if (result != null)
                                sendReply(replyStream, result);
                            else if (!contactInfo[0].isEmpty())
                                sendReply(replyStream, "No unread messages found with " +
                                        contactInfo[0] + ".");
                            else {
                                sendReply(replyStream, "No unread messages.");
                            }
                        }
                        catch(SecurityException e) {
                            sendReply(replyStream, "Could not retrieve messages: make sure " +
                                    getString(R.string.app_name) + " has SMS permission.");
                            Log.w(TAG, "No SMS permission for reading.");
                        }
                    }
                    else {
                        //should never happen
                        sendReply(replyStream, "'" + command + "' is a not a recognized command. " +
                                "Please report this issue on GitHub.");
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Socket error occurred.", e);
                }
            }
        }
    }

    /**
     * Wrapper for printStream.println which sends a length header followed by \n before the body
     * to make it easier for the client to properly receive all data.
     * @param replyStream Stream to print message to.
     * @param msg Message to send.
     */
    private void sendReply(PrintStream replyStream, String msg) {
        int len = msg.length();

        final int HEADER_LEN = 32;
        String lenStr = "" + len;

        //if msg.length() >= 10^32, the stream will get stuck. this _probably_ won't happen.
        if(lenStr.length() < HEADER_LEN) {
            //right-pad the header with spaces
            lenStr = String.format("%1$-" + HEADER_LEN + "s", lenStr);
        }

        String response = lenStr + '\n' + msg;
        replyStream.println(response);
    }

    private String getConversation(String[] contactInfo, int numberToRetrieve)
            throws SecurityException {
        ContentResolver contentResolver = getContentResolver();
        Uri uri = Uri.parse("content://sms/");
        final String[] projection = new String[]{"date", "body", "type", "address", "read"};

        Cursor query = contentResolver.query(uri, projection, null, null, "date desc");
        //Log.d(TAG, "Query selection is " + selection);

        if(query == null) {
            Log.e(TAG, "Null Cursor trying to get conversation with " + contactInfo[1] + ", # " +
                    contactInfo[0]);
            return null;
        }
        else if(query.getCount() == 0) {
            Log.e(TAG, "No result trying to get conversation with " + contactInfo[1] + ", # " +
                    contactInfo[0]);

            query.close();
            return null;
        }

        int count = 0;
        List<String> messages = new ArrayList<>(numberToRetrieve);
        List<Long> dates = new ArrayList<>(numberToRetrieve);

        //this will succeed because already checked query's count
        query.moveToFirst();
        int index_date = query.getColumnIndex("date");
        int index_body = query.getColumnIndex("body");
        int index_type = query.getColumnIndex("type");
        int index_addr = query.getColumnIndex("address");

//        Log.d(TAG, "Successful sms query for " + contactInfo[1] + ", address is " +
//                query.getString(query.getColumnIndex("address")));

        do {
            String addr = query.getString(index_addr);
            //Skip all texts that aren't from the requested contact, if one was given.
            if(contactInfo != null && !PhoneNumberUtils.compare(addr, contactInfo[0]))
                continue;

            String body = query.getString(index_body);
            int type = query.getInt(index_type);
            long time = query.getLong(index_date);

            //add sender to the message
            final String YOU = "You", SENDER_SUFFIX = ": ";
            String sender = YOU;
            String otherSender = contactInfo[1];
            if(type == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX) {
                sender = contactInfo[1];
                otherSender = YOU;
            }
            sender += SENDER_SUFFIX;
            otherSender += SENDER_SUFFIX;

            String message = Utilities.formatSms(sender, otherSender, body, time);

            //date formatting is done below so store the time for that
            dates.add(time);
            messages.add(message);

            count++;
        } while(query.moveToNext() && count < numberToRetrieve);

        query.close();

        if(messages.isEmpty())
            return null;

        //reverse the conversation messages so they can be read top-to-bottom as is natural
        Collections.reverse(messages);
        Collections.reverse(dates);

        return messagesIntoOutput(messages, dates);
    }

    //will return List<String> when it is confirmed to work.
    private String getUnread(String phoneNumber, int numberToRetrieve) {
        Log.d(TAG, "Getting unread");
        Uri uri = Uri.parse("content://sms/inbox");

        Cursor query = getContentResolver().query(uri, null, null, null, null);

        if(query == null) {
            Log.e(TAG, "Null Cursor trying to get unread.");
            return null;
        }
        else if(query.getCount() == 0) {
            Log.e(TAG, "No result trying to get unread.");

            query.close();
            return null;
        }

        List<String> messages = new ArrayList<>();
        List<Long> dates = new ArrayList<>();

        //this will succeed because already checked query's count
        query.moveToFirst();

        int count = 0;
        //DEBUG ONLY
        int seen_count = 0;
        int read_count = 0;
        int seen_and_read_count = 0;
        int index_date = query.getColumnIndex("date");
        int index_body = query.getColumnIndex("body");
        int index_read = query.getColumnIndex("read");
        int index_seen = query.getColumnIndex("seen");
        int index_addr = query.getColumnIndex("address");
        do {
            String addr = query.getString(index_addr);
            if(!phoneNumber.isEmpty() && !PhoneNumberUtils.compare(addr, phoneNumber))
                continue;

            String body = query.getString(index_body);
            int spaceIndex = body.indexOf(' ');
            if(spaceIndex != -1)
                body = body.substring(0, spaceIndex);
            int read = query.getInt(index_read);
            int seen = query.getInt(index_seen);
            long time = query.getLong(index_date);

            if(read == 1) {
                if(seen == 1) {
                    seen_and_read_count++;
                }
                else {
                    read_count++;
                }
            }
            else if(seen == 1) {
                seen_count++;
            }

            //type 1 is received, type 2 is sent
            Log.d(TAG, String.format("Seen: %d Read: %d Date: %s Addr: %s Body: %s",
                    seen, read, Utilities.unixTimeToTime(time), addr, body));

            count++;
        } while(query.moveToNext() && count < numberToRetrieve);

        if(phoneNumber.isEmpty()) { phoneNumber = "anyone"; }
        return String.format(Locale.getDefault(), "Read %d received messages from %s. %d were seen and " +
                "read, %d were read but not seen, %d were seen but not read.",
                count, phoneNumber, seen_and_read_count, read_count, seen_count);
    }

    private String messagesIntoOutput(List<String> messages, List<Long> dates) {
        //combine the messages into one string to be sent to the client
        StringBuilder resultBuilder = new StringBuilder();
        //Generate and print a new date header each time the next message is from a different day
        String lastUsedDate = null;
        for(int i = 0; i < messages.size(); i++) {
            String currentDate = Utilities.unixTimeToRelativeDate(dates.get(i));

            if(lastUsedDate == null) {
                //if Today is the first (and therefore only, there can't be texts in the future)
                //date header, don't print it
                if(!currentDate.equals("Today")) {
                    //update lastUsedDate to the first
                    lastUsedDate = currentDate;
                    resultBuilder.append("--- ").append(currentDate).append(" ---").append('\n');
                }
            }
            //else if this is not the first date header and the date header has changed,
            //update and print the date header
            else if(!currentDate.equals(lastUsedDate)) {
                lastUsedDate = currentDate;
                resultBuilder.append("--- ").append(currentDate).append(" ---").append('\n');
            }

            resultBuilder.append(messages.get(i)).append('\n');
        }
        return resultBuilder.toString();
    }

    /**
     * Accepts contact name (case insensitive), returns:
     * @return String[2] where [0] is the contact's phone number,
     * and [1] is the contact's name as stored in the phone. Returns null if contact not found.
     * @throws SecurityException If Contacts permission is not given.
     */
    private String[] getContactInfo(String name) throws SecurityException {
        //TODO what if they have multiple phone numbers - return a bigger array with the extras?
        String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME +
                " like'%" + name + "%'";
        String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME};
        Cursor c;
        try {
            c = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection, selection, null, null);
        } catch (SecurityException e) {
            Log.e(TAG, "No 'Contacts' permission, cannot proceed.");
            throw(e);
        }
        String[] contactInfo = {null, null};
        try {
            if (c != null) {
                if (c.moveToFirst()) {
                    contactInfo[0] = Utilities.removeNonDigitCharacters(c.getString(0));
                    //name as stored in contacts
                    contactInfo[1] = c.getString(1);
                } else {
                    Log.e(TAG, "No result for getting phone number of " + name);
                    return null;
                }
            }
            else {
                Log.e(TAG, "Received nonexistent contact " + name);
                return null;
            }
        }
        finally {
            if(c != null)
                c.close();
        }
        return contactInfo;
    }

    private String getContactName(String phoneNumber) throws SecurityException {
        //TODO what if they have multiple phone numbers - return a bigger array with the extras?
        String selection = ContactsContract.CommonDataKinds.Phone.NUMBER +
                " like'%" + phoneNumber + "%'";
        String[] projection = new String[]{ ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME };
        Cursor c;
        try {
            c = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection, selection, null, null);
        } catch (SecurityException e) {
            Log.e(TAG, "No 'Contacts' permission, cannot proceed.");
            throw(e);
        }
        String contactInfo = null;
        try {
            if (c != null) {
                if (c.moveToFirst()) {
                    contactInfo = c.getString(0);
                } else {
                    Log.e(TAG, "No result for getting name of " + phoneNumber);
                    return null;
                }
            }
            else {
                Log.e(TAG, "No contact for number " + phoneNumber);
                return null;
            }
        }
        finally {
            if(c != null)
                c.close();
        }
        return contactInfo;
    }
}

/**
 * Accepts a message and a phone number, and sends the message. Returns success.
 */
class SmsSendThread extends AsyncTask<String, Void, Boolean> {
    private static final String TAG = "SmsSendThread";

    @Override
    protected Boolean doInBackground(String... params) {
        //Log.d(TAG, "About to send " + params[1] + " to " + params[0]);

        SmsManager sms = SmsManager.getDefault();

        try {
            sms.sendTextMessage(params[0], null, params[1], null, null);
        }
        catch(SecurityException se) {
            Log.w(TAG, "No SMS permission when sending.");
            return false;
        }
        catch(Exception e) {
            Log.e(TAG, "Exception occurred sending SMS", e);
            return false;
        }

        return true;
    }
}
