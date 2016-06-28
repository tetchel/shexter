package ca.tetchel.shexter;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;

public class SmsServerService extends Service {

    private static final String TAG = "Shexter_SmsServer";

    private static final int PORT = 5678;
    private ServerSocket serverSocket;
    private ServerThread serverThread;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        serverThread = new ServerThread();
        serverThread.start();

        //TODO ask for permission

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
    }

    private class ServerThread extends Thread {
        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(PORT);
            }
            catch(IOException e) {
                Log.e(TAG, "Exception opening ServerSocket", e);
                //TODO terminate the thread / ask the user to restart the app or something
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
                    //second line for contact name
                    String contact_name_input = in_reader.readLine();
                    //then get the contact's phone number from his/her name
                    String[] contactInfo = null;
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

                    if(errMsg != null) {
                        //if something has gone wrong already, don't do anything else
                        sendReply(replyStream, errMsg);
                    }
                    else if(command.equals("send")) {
                        String line;
                        StringBuilder msgBodyBuilder = new StringBuilder();
                        //read the message body into msgBodyBuilder
                        while((line = in_reader.readLine()) != null) {
                            if(line.isEmpty())
                                break;
                            msgBodyBuilder.append(line).append('\n');
                        }

                        String message = msgBodyBuilder.toString();
                        if(message.isEmpty()) {
                            //this is already checked client-side, but just to be safe...
                            sendReply(replyStream, "Not sent: message body was empty.");
                            continue;
                        }

                        //chop off last newline
                        message = message.substring(0, message.length()-1);

                        SmsSendThread sendThread = new SmsSendThread();
                        //attempt to send sms and handle errors appropriately
                        try {
                            Boolean result = sendThread.execute(contactInfo[0], message).get();
                            if(result) {
                                sendReply(replyStream, "Successfully sent message to " +
                                        contactInfo[1] + ".");
                                Log.d(TAG, "Successful send to " + contactInfo[1] + ", " +
                                        contactInfo[0]);
                            }
                            else {
                                sendReply(replyStream, "Could not send message: make sure " +
                                        getString(R.string.app_name) + " has SMS permission.");
                                Log.w(TAG, "No SMS permission for sending.");
                            }
                        }
                        catch(InterruptedException | ExecutionException e) {
                            sendReply(replyStream, "Serious error in the SMS send thread; " +
                                    "please report this issue on GitHub.");
                            Log.e(TAG, "Exception from sendThread", e);
                        }
                    }
                    else if(command.equals("read")) {
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
                    else {
                        //should never happen
                        sendReply(replyStream, command + " is a not a recognized command. " +
                                "Please report this issue on GitHub");
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

    /**
     * There are many different 'valid' forms of phone number. <br>
     * It can:
     * <ul>
     * <li>Start with '+1' in addition to the regular 10 digits.</li>
     * <li>Start with '1' in addition to the regular 10 digits.</li>
     * <li>Start with 1 or +1 and have a space after the prefix.</li>
     * <li>Be hyphenated or not, including with any of the above prefixes.</li>
     * <li>Just be the normal 10-digit-only number.</li>
     * </ul>
     * In this function, build a WHERE clause
     * for the 'selection' parameter of the sms query to try and cover all these bases. <br>
     * Will have to add more as they come along.
     * @param contactNumber Phone number to turn into various forms and be matched using OR. Must
     *                      already be stripped to digits by contact getting code.
     * @return A SQL WHERE clause (without the WHERE) which allows the phone number to match any
     * of these forms.
     */
    private String buildWhereClause(String contactNumber) {
        StringBuilder whereBuilder = new StringBuilder();
        final String addr = "address='";
        final String OR = "' OR ";
        //In case the 1 is already included:
        if(contactNumber.length() != 10) {
            //cut off the 1
            contactNumber = contactNumber.substring(1);
            if(contactNumber.length() != 11) {
                Log.e(TAG, "buildWhereClause received invalid phone number: " + contactNumber);
            }
        }

        //+1 at start
        whereBuilder.append(addr).append("+1").append(contactNumber).append(OR);
        //1 at start
        whereBuilder.append(addr).append("1").append(contactNumber).append(OR);
        //+1  at start
        whereBuilder.append(addr).append("+1 ").append(contactNumber).append(OR);
        //1  at start
        whereBuilder.append(addr).append("1 ").append(contactNumber).append(OR);

        //same as above, but hyphenated
        String hyphenated = Utilities.hyphenatePhoneNumber(contactNumber);
        whereBuilder.append(addr).append(hyphenated).append(OR);
        whereBuilder.append(addr).append("+1").append(hyphenated).append(OR);
        whereBuilder.append(addr).append("1").append(hyphenated).append(OR);
        whereBuilder.append(addr).append("+1 ").append(hyphenated).append(OR);
        whereBuilder.append(addr).append("1 ").append(hyphenated).append(OR);

        //normal
        whereBuilder.append(addr).append(contactNumber).append('\'');

        return whereBuilder.toString();
    }

    private String getConversation(String[] contactInfo, int numberToRetrieve)
            throws SecurityException {
        ContentResolver contentResolver = getContentResolver();
        final String[] projection = new String[]{"date", "body", "type", "address"};
        final String selection = buildWhereClause(contactInfo[0]);
        Uri uri = Uri.parse("content://sms/");
        Cursor query = contentResolver.query(uri, projection, selection, null, "date desc");
        Log.d(TAG, "Query selection is " + selection);

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
        List<String> conversations = new ArrayList<>(numberToRetrieve);
        List<Long> times = new ArrayList<>(numberToRetrieve);

        query.moveToFirst();
        int index_date = query.getColumnIndex("date");
        int index_body = query.getColumnIndex("body");
        int index_type = query.getColumnIndex("type");

        Log.d(TAG, "Successful sms query for " + contactInfo[1] + ", address is " +
                query.getString(query.getColumnIndex("address")));

        do {
            String body = query.getString(index_body);
            int msgType = query.getInt(index_type);
            long time = query.getLong(index_date);

            StringBuilder messageBuilder = new StringBuilder();

            //TODO format better, especially with texts with newlines in them and long texts
            //a left/right conversation display like real texting would be stellar!
            String niceTime = '[' +  Utilities.unixTimeToTime(time) + "] ";
            messageBuilder.append(niceTime);

            if(msgType == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX) {
                messageBuilder.append(contactInfo[1]).append(": ").append(body);
            }
            else if(msgType == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT) {
                messageBuilder.append("You").append(": ").append(body);
            }

            times.add(time);
            conversations.add(messageBuilder.toString());

            count++;
        } while(query.moveToNext() && count < numberToRetrieve);

        query.close();

        //reverse the conversation messages so they can be read top-to-bottom as is natural
        Collections.reverse(conversations);
        Collections.reverse(times);

        //combine the messages into one string to be sent to the client
        StringBuilder resultBuilder = new StringBuilder();
        //Generate and print a new date header each time the next message is from a different day
        String lastUsedDate = null;
        for(int i = 0; i < conversations.size(); i++) {
            String currentDate = Utilities.unixTimeToRelativeDate(times.get(i));

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

            resultBuilder.append(conversations.get(i)).append('\n');
        }

        return resultBuilder.toString();
    }

    /**
     * Accepts contact name (case insensitive) and application context, returns:
     * @return String[2] where [0] is the contact's phone number,
     * and [1] is the contact's name as stored in the phone. Returns null if contact not found.
     * @throws SecurityException If Contacts permission is not given.
     */
    private String[] getContactInfo(String name) throws SecurityException {
        //TODO what if they have multiple phone numbers
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
            //No contacts permission error
            Log.e(TAG, "No 'Contacts' permission, cannot proceed.");
            throw(e);
        }
        String[] contactInfo = {null, null};
        try {
            if (c != null) {
                if (c.moveToFirst()) {
                    //maybe here could get the contact full name too and return that
                    //for a confirmation message, eg. "Sent to Contact Name"
                    //number
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

    /**
     * Accepts a message and a phone number, and sends the message. Returns success.
     */
    private class SmsSendThread extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... params) {
            //Log.d(TAG, "About to send " + params[1] + " to " + params[0]);

            SmsManager sms = SmsManager.getDefault();

            try {
                sms.sendTextMessage(params[0], null, params[1], null, null);
            }
            catch(SecurityException se) {
                Log.e(TAG, "No SMS permission when sending.");
                return false;
            }
            catch(Exception e) {
                Log.e(TAG, "Exception occurred sending SMS", e);
                return false;
            }

            return true;
        }
    }
}
