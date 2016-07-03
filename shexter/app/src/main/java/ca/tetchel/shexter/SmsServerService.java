package ca.tetchel.shexter;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    private String getConversation(String[] contactInfo, int numberToRetrieve)
            throws SecurityException {
        ContentResolver contentResolver = getContentResolver();
        final String[] projection = new String[]{"date", "body", "type", "address"};
        //final String selection = buildWhereClause(contactInfo[0]);
        Uri uri = Uri.parse("content://sms/");
        Cursor query = contentResolver.query(uri, projection, null, null, "date desc");
        //Log.d(TAG, "Query selection is " + selection);

        //TODO fix some of noah's messages not appearing. Need to query texts from both sender and
        //receiver, and inspect both of their phone numbers ('address'es)
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
        List<Long> times = new ArrayList<>(numberToRetrieve);

        query.moveToFirst();
        int index_date = query.getColumnIndex("date");
        int index_body = query.getColumnIndex("body");
        int index_type = query.getColumnIndex("type");
        int index_addr = query.getColumnIndex("address");

        Log.d(TAG, "Successful sms query for " + contactInfo[1] + ", address is " +
                query.getString(query.getColumnIndex("address")));

        do {
            String addr = query.getString(index_addr);
            //Skip all texts that aren't from the requested contact.
            if(!PhoneNumberUtils.compare(addr, contactInfo[0]))
                continue;

            String body = query.getString(index_body);
            int type = query.getInt(index_type);
            long time = query.getLong(index_date);

            StringBuilder messageBuilder = new StringBuilder();

            //a left/right conversation display like real texting would be stellar!
            String niceTime = '[' +  Utilities.unixTimeToTime(time) + "] ";
            messageBuilder.append(niceTime);

            //add sender to the message
            String sender = "You: ";
            if(type == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX) {
                sender = contactInfo[1] + ": ";
            }

            messageBuilder.append(sender);

            final int LINE_LEN = 100;
            //keep lines shorter than LINE_LEN chars. User could configure this, but not through the
            //client, unless there were other settings so could implement a settings request.
            //Could have Client Settings (.ini) and Server Settings (through app)
            int remainingLineChars = LINE_LEN - niceTime.length() - sender.length();
            int bodyIndex = 0;
            while(bodyIndex < body.length()) {
                int remaining = body.length() - bodyIndex;
                String subBody;
                if(remaining < remainingLineChars) {
                    subBody = body.substring(bodyIndex);
                    bodyIndex += subBody.length();
                }
                else {
gi                    subBody = body.substring(bodyIndex, bodyIndex + remainingLineChars);
                    bodyIndex += subBody.length();
                    subBody += '\n';
                }
                messageBuilder.append(subBody);
            }
            //TODO format message bodies with newlines in them better (inc. long ones)

            times.add(time);
            messages.add(messageBuilder.toString());

            count++;
        } while(query.moveToNext() && count < numberToRetrieve);

        query.close();

        //reverse the conversation messages so they can be read top-to-bottom as is natural
        Collections.reverse(messages);
        Collections.reverse(times);

        //combine the messages into one string to be sent to the client
        StringBuilder resultBuilder = new StringBuilder();
        //Generate and print a new date header each time the next message is from a different day
        String lastUsedDate = null;
        for(int i = 0; i < messages.size(); i++) {
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

            resultBuilder.append(messages.get(i)).append('\n');
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
            Log.d(TAG, "About to send to " + params[0]);

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
}
