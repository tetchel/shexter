package ca.tetchel.shexter;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.PowerManager;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class SmsServerService extends Service {

    private static final String TAG = "SmsServer";

    // Singleton instance to be called to get access to the Application Context from static code
    private static SmsServerService INSTANCE;

    private static final int PORT = 5678;

    private static final String COMMAND_SEND = "send",
            COMMAND_READ = "read",
            COMMAND_UNREAD = "unread",
            COMMAND_SETPREF = "setpref",
            // flag for a lone setpref request (not a read or send)
            COMMAND_SETPREF_LIST = COMMAND_SETPREF + "-list",
            // flag to send back to the client when setpref required
            SETPREF_REQUIRED = "NEED-SETPREF",
            COMMAND_CONTACTS = "contacts",
            UNREAD_CONTACT_FLAG = "-contact",
            NUMBER_FLAG = "-number";


    private ServerSocket serverSocket;
    private ServerThread serverThread;

    private SmsReceiver receiver;

    /**
     * Access the singleton instance of this class for getting the context.
     *
     * @return The singleton instance.
     */
    public static SmsServerService instance() {
        return INSTANCE;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        serverThread = new ServerThread();
        serverThread.start();
        INSTANCE = this;

        //Register to receive new SMS intents
        receiver = new SmsReceiver();

        Log.d(TAG, getString(R.string.app_name) + " service started.");

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        serverThread.interrupt();
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Exception closing ServerSocket", e);
        }

        Log.d(TAG, getString(R.string.app_name) + " service stopped.");
    }

    private class ServerThread extends Thread {
        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(PORT);
            } catch (IOException e) {
                Log.e(TAG, "Exception opening ServerSocket", e);
                Toast.makeText(getApplicationContext(), "An exception occurred opening the server "
                        + "socket; the app will not work in its current state. Please try "
                        + "restarting the app and possibly your phone.", Toast.LENGTH_LONG).show();
                onDestroy();
            }
            // used to track previous command when setpref is used.
            String oldRequest = null;
            while (!Thread.currentThread().isInterrupted() && !serverSocket.isClosed()) {
                try {
                    //region SetupServer
                    Log.d(TAG, "Ready to accept");
                    Socket socket = serverSocket.accept();
                    // make sure phone stays awake - does this even work?
                    PowerManager.WakeLock wakeLock = ((PowerManager) getSystemService(POWER_SERVICE))
                            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                                    getString(R.string.app_name));
                    wakeLock.acquire();

                    // print back to socket using this
                    PrintStream replyStream = new PrintStream(socket.getOutputStream());
                    //endregion
                    // Store the full request
                    Scanner scansAll;
                    String request;
                    try {
                        // Do not close this - it will close when the socket is closed in onDestroy
                        // TODO use a length header. This will break if message starts with newline!
                        scansAll = new Scanner(socket.getInputStream()).useDelimiter("\n\n");
                        request = scansAll.hasNext() ? scansAll.next() : "";
                    }
                    catch(IOException e) {
                        Log.e(TAG, "Error scanning request", e);
                        continue;
                    }

                    // Facilitates reading line-by-line
                    BufferedReader requestReader = new BufferedReader(new StringReader(request));

                    //The request protocol is command\ncontact\ncommand-specific-data
                    //so read the first line to get the command
                    String command = requestReader.readLine();
                    Log.d(TAG, "Received command: " + command);
                    //region GetContact
                    String contactGetResult = null;
                    Contact contact = null;
                    // Determine if the command requires a contact.
                    if (COMMAND_READ.equals(command) || COMMAND_SEND.equals(command) ||
                            COMMAND_SETPREF_LIST.equals(command) || COMMAND_SETPREF.equals(command) ||
                            (COMMAND_UNREAD + UNREAD_CONTACT_FLAG).equals(command)) {

                        //second line for contact name
                        String contactNameInput = requestReader.readLine();
                        try {
                            if (contactNameInput.equals(NUMBER_FLAG)) {
                                // if number flag was given, don't retrieve contact, build one
                                // with the number
                                String number = requestReader.readLine();
                                contact = new Contact(number, Collections.singletonList(number));
                            }
                            else {
                                // get the contact's info from name
                                contact = Utilities.getContactInfo(contactNameInput);
                            }

                            if (contact == null) {
                                //non existent contact
                                contactGetResult = "Couldn't find contact '" + contactNameInput +
                                        "' with any associated phone numbers, please make sure the "
                                        + "contact exists and is spelled correctly.";
                            }
                            // Validate the contact / do any additional work
                            else if (contact.count() == 0) {
                                contactGetResult = "You have no phone number associated with " +
                                        contact.name() + ".";
                            }
                            else if (contact.count() == 1 && !contact.hasPreferred()) {
                                //will automatically pick the first/only number
                                contact.setPreferred(0);
                            }
                            else if (contact.count() > 1 && !contact.hasPreferred()
                                    && !command.equals(COMMAND_SETPREF)) {
                                // reply to the client requesting that user pick a preferred #
                                oldRequest = request;
                                command = COMMAND_SETPREF_LIST;
                            }
                        } catch (SecurityException e) {
                            contactGetResult = "Could not retrieve contact info: make sure " +
                                    getString(R.string.app_name) + " has Contacts permission.";
                        }
                    }
                    if (contactGetResult != null) {
                        //if something has gone wrong already, don't do anything else
                        sendReply(replyStream, contactGetResult);
                    }
                    //endregion
                    else {
                        String response = commandProcessor(command, oldRequest, contact,
                                requestReader);
                        sendReply(replyStream, response);
                    }
                    wakeLock.release();
                } catch (IOException e) {
                    Log.e(TAG, "Socket error occurred.", e);
                }
            }
        }
    }

    /**
     * Wrapper for printStream.println which sends a length header followed by \n before the body
     * to make it easier for the client to properly receive all data.
     *
     * @param replyStream Stream to print message to.
     * @param msg         Message to send.
     */
    private void sendReply(PrintStream replyStream, String msg) {
        int len = msg.length();

        final int HEADER_LEN = 32;
        String header = "" + len;

        //if msg.length() >= 10^32, the stream will get stuck. this _probably_ won't happen.
        if (header.length() < HEADER_LEN) {
            //right-pad the header with spaces
            header = String.format("%1$-" + HEADER_LEN + "s", header);
        }

        String response = header + '\n' + msg;
        replyStream.println(response);
    }

    /**
     * @return The response to be returned to the client.
     * @throws IOException
     */
    private String commandProcessor(String command, String originalRequest, Contact contact,
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
            // receive the index to set the new preferred number to.
            int replyIndex = Integer.parseInt(requestReader.readLine());
            Log.d(TAG, "Setting " + contact.name() + " pref to " + replyIndex);
            contact.setPreferred(replyIndex);

            // Get the original command's data - will do a recursive call to perform it.
            BufferedReader originalRequestReader = new BufferedReader(new StringReader(originalRequest));
            String originalCommand = originalRequestReader.readLine();
            // Remove the contact from the original - we already know it
            originalRequestReader.readLine();

            if(COMMAND_SETPREF.equals(originalCommand)) {
                return "Changed " + contact.name() + "'s preferred number to: " +
                        contact.preferred();
            }
            else {
                // still need to perform the original command
                return commandProcessor(originalCommand, "", contact, originalRequestReader);
            }
        }
        else if (COMMAND_CONTACTS.equals(command)) {
            try {
                String allContacts = Utilities.getAllContacts();
                if (allContacts != null && !allContacts.isEmpty()) {
                    return allContacts;
                }
                else {
                    return "An error occurred getting contacts, or you have no contacts!";
                }
            } catch (SecurityException e) {
                return "No Contacts permission! Open the " + getString(R.string.app_name) +
                        " app and give Contacts permission.";
            }
        }
        else {
            //should never happen
            return "'" + command + "' is a not a recognized command. " +
                    "Please report this issue on GitHub.";
        }
    }

    private String sendCommand(Contact contact, BufferedReader requestReader)
            throws IOException {
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

            return String.format(Locale.getDefault(), "Successfully sent %d message%s to %s%s.",
                    numberSent, numberSent != 1 ? "s" : "", contact.name(), preferred);
        } catch (SecurityException e) {
            return "No SMS permission! Open the " + getString(R.string.app_name) +
                    " app and give SMS permission.";
        } catch (Exception e) {
            Log.e(TAG, "Exception from sendThread", e);
            return "Unexpected exception in the SMS send thread; " +
                    "please report this issue on GitHub.";
        }
    }

    private String readCommand(Contact contact, BufferedReader requestReader)
            throws IOException {
        Log.d(TAG, "Reading from " + contact.preferred());
        int numberToRetrieve = Integer.parseInt(requestReader.readLine());
        int outputWidth = Integer.parseInt(requestReader.readLine());

        try {
            String convo = Utilities.getConversation(contact, numberToRetrieve,
                    outputWidth);

            if (convo != null) {
                receiver.removeMessagesFromNumber(contact.preferred());
                Log.d(TAG, "Responded with convo with " + contact.name());
                return convo;
            }
            else {
                String response = "No messages found with " + contact.name();
                if (!contact.name().equals(contact.preferred())) {
                    // Don't display the preferred twice in the -n case.
                    response += ", " + contact.preferred() + ".";
                }
                return response + '.';
            }

        } catch (SecurityException e) {
            Log.w(TAG, "No SMS permission for reading.");
            return "No SMS permission! Open the " + getString(R.string.app_name) +
                    " app and give SMS permission.";
        }
    }

    private String unreadCommand(BufferedReader requestReader)
            throws IOException {
        int outputWidth = Integer.parseInt(requestReader.readLine());
        try {
            String unread = receiver.getAllSms(outputWidth);

            if (unread != null && !unread.isEmpty()) {
                unread = "Unread Messages:\n" + unread;
                return unread;
            }
            else {
                return "No unread messages.";
            }
        } catch (SecurityException e) {
            Log.w(TAG, "No SMS permission for reading.");
            return "Could not retrieve messages: make sure " +
                    getString(R.string.app_name) + " has SMS permission.";
        }
    }
}

/**
 * Accepts a message and a phone number, and sends the message. Returns number sent.
 */
class SmsSendThread extends AsyncTask<String, Void, Integer> {
    //    private static final String TAG = "SmsSendThread";

    @Override
    protected Integer doInBackground(String... params) {
        //Log.d(TAG, "About to send " + params[1] + " to " + params[0]);
        SmsManager smsm = SmsManager.getDefault();
        ArrayList<String> divided = smsm.divideMessage(params[1]);
        // could wait for the message to _actually_ be sent using PendingIntents
        if(divided.size() > 1) {
            smsm.sendMultipartTextMessage(params[0], null, divided, null, null);
        }
        else {
            smsm.sendTextMessage(params[0], null, params[1], null, null);
        }

        return divided.size();
    }
}