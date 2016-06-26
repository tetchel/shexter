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
                    //The protocol is command\ncontact\nmessage,
                    //so read the first line to get the command
                    String command = in_reader.readLine();
                    //second line for contact name
                    String contact_name_input = in_reader.readLine();
                    //then get the contact's phone number from his/her name
                    String[] contactInfo = null;
                    try {
                        contactInfo = getContactInfo(contact_name_input, SmsServerService.this);

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
                        replyStream.println(errMsg);
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
                        //chop off last newline
                        message = message.substring(0, message.length()-1);

                        SmsSendThread sendThread = new SmsSendThread();
                        //attempt to send sms and handle errors appropriately
                        try {
                            Boolean result = sendThread.execute(contactInfo[0], message).get();
                            if(result) {
                                replyStream.println("Successfully sent message to " + contactInfo[1]
                                        + ".");
                            }
                            else {
                                replyStream.println("Could not send message: make sure " +
                                        getString(R.string.app_name) + " has SMS permission.");
                            }
                        }
                        catch(InterruptedException | ExecutionException e) {
                            replyStream.println("Serious error in the SMS send thread; " +
                                    "please report this issue on GitHub.");
                        }
                    }
                    else if(command.equals("read")) {
                        String convo;
                        try {
                            //TODO take this number as a command line arg
                            convo = getConversation(contactInfo, 20);

                            if(convo != null)
                                replyStream.println(convo);
                            else
                                replyStream.println("Error occurred getting conversation with "
                                        + contactInfo[1] + ".");
                        }
                        catch(SecurityException e) {
                            replyStream.println("Could not retrieve messages: make sure " +
                                    getString(R.string.app_name) + " has SMS permission.");
                        }
                    }
                    else {
                        //should never happen
                        replyStream.println(command + " is a not a recognized command. " +
                                "Please report this issue on GitHub");
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Socket error occurred.", e);
                }
            }
        }
    }

    private String getConversation(String[] contactInfo, int numberToRetrieve)
            throws SecurityException {
        ContentResolver contentResolver = getContentResolver();
        final String[] projection = new String[]{"date", "body", "type"};
        final String selection = "address=" + removeNonDigitCharacters(contactInfo[0]);
        Uri uri = Uri.parse("content://sms/");
        Cursor query = contentResolver.query(uri, projection, selection, null, "date desc");
        //TODO bugs out on some numbers (allan, joseph, dad) but why these ones???

        if(query == null) {
            Log.e(TAG, "Null Cursor trying to get conversation with " + contactInfo[1]);
            return null;
        }
        else if(query.getCount() == 0) {
            Log.e(TAG, "No result trying to get conversation with " + contactInfo[1]);
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

        do {
            String body = query.getString(index_body);
            int msgType = query.getInt(index_type);
            long time = query.getLong(index_date);

            StringBuilder messageBuilder = new StringBuilder();

            //TODO format better, especially with texts with newlines in them and long texts
            //a left/right conversation display like real texting would be stellar!
            messageBuilder.append('[').append(unixTimeToTime(time)).append("] ");

            if(msgType == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX) {
                messageBuilder.append(contactInfo[1]).append(": ").append(body);
            }
            else if(msgType == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT) {
                messageBuilder.append("You: ").append(body);
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
            String currentDate = unixTimeToRelativeDate(times.get(i));

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

    private static String removeNonDigitCharacters(String input) {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if(Character.isDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    //Determine how old the message is and assign a date label accordingly\
    //avert your eyes from this horror and just trust that it works
    private static String unixTimeToRelativeDate(long unixTime) {
        Date inputDate = new Date(unixTime);

        Calendar cal = Calendar.getInstance();

        Date today = cal.getTime();
        //subtract a day to get yesterday's date
        cal.add(Calendar.DATE, -1);
        Date yesterday = cal.getTime();
        //subtract 6 more days to get days that were this week
        cal.add(Calendar.DATE, -6);
        Date lastWeek = cal.getTime();
        //look only at the year field for this one, ie if it's 2016, all dates not from
        //2016 will be display with their year.
        //TODO have a text from last year to test this...
        cal.set(Calendar.MONTH, 0);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        Date startOfThisYear = cal.getTime();

        if(compareDatesWithoutTime(inputDate, today) == 0) {
            return "Today";
        }
        else if (compareDatesWithoutTime(inputDate, yesterday) == 0){
            return "Yesterday";
        }
        else if (inputDate.after(lastWeek)) {
            //return the day of week
            SimpleDateFormat sdf = new SimpleDateFormat("EEEE", Locale.getDefault());
            return sdf.format(inputDate);
        }
        else if(inputDate.after(startOfThisYear)) {
            //"January 1"
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd", Locale.getDefault());
            return sdf.format(inputDate);
        }
        else {
            //not from this year, include the year
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
            return sdf.format(inputDate);
        }
    }

    //the java.util.Date api really sucks
    private static int compareDatesWithoutTime(Date d1, Date d2) {
        Calendar c1 = Calendar.getInstance();
        Calendar c2 = Calendar.getInstance();
        c1.setTime(d1);
        c1.set(Calendar.MILLISECOND, 0);
        c1.set(Calendar.SECOND, 0);
        c1.set(Calendar.MINUTE, 0);
        c1.set(Calendar.HOUR_OF_DAY, 0);
        c2.setTime(d2);
        c2.set(Calendar.MILLISECOND, 0);
        c2.set(Calendar.SECOND, 0);
        c2.set(Calendar.MINUTE, 0);
        c2.set(Calendar.HOUR_OF_DAY, 0);
        return c1.getTime().compareTo(c2.getTime());
    }

    private static String unixTimeToTime(long unixTime) {
        Date d = new Date(unixTime);
        DateFormat df = new SimpleDateFormat("HH:mm", Locale.getDefault());
        df.setTimeZone(TimeZone.getDefault());

        return df.format(d);
    }

    /**
     * Accepts contact name (case insensitive) and application context, returns:
     * @return String[2] where [0] is the contact's phone number,
     * and [1] is the contact's name as stored in the phone. Returns null if contact not found.
     * @throws SecurityException If Contacts permission is not given.
     */
    private String[] getContactInfo(String name, Context context) throws SecurityException {
        //TODO what if they have multiple phone numbers
        String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME +
                " like'%" + name + "%'";
        String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME};
        Cursor c;
        try {
            c = context.getContentResolver().query(
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
                    contactInfo[0] = c.getString(0);
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

    private class SmsSendThread extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... params) {
            Log.d(TAG, "About to send " + params[1] + " to " + params[0]);

            SmsManager sms = SmsManager.getDefault();
            //TODO log all caught exceptions
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
