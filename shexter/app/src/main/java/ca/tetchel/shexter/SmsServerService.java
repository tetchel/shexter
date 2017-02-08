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
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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
            String originalCommand = null;
            while (!Thread.currentThread().isInterrupted() && !serverSocket.isClosed()) {
                try {
                    //region SetupServer
                    Log.d(TAG, "Ready to accept");
                    Socket socket = serverSocket.accept();
                    // make sure phone stays awake
                    PowerManager.WakeLock wakeLock = ((PowerManager) getSystemService(POWER_SERVICE))
                            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                                    getString(R.string.app_name));
                    wakeLock.acquire();

                    //reader from socket
                    BufferedReader inReader = new BufferedReader(
                            new InputStreamReader(socket.getInputStream()));
                    //print back to socket
                    PrintStream replyStream = new PrintStream(socket.getOutputStream());
                    //endregion
                    //The request protocol is command\ncontact\n command-specific-data
                    //so read the first line to get the command
                    String command = inReader.readLine();
                    Log.d(TAG, "Received command: " + command);
                    //region GetContact
                    String contactGetResult = null;
                    Contact contact = null;
                    // Determine if the command requires a contact.
                    if (COMMAND_READ.equals(command) || COMMAND_SEND.equals(command) ||
                            COMMAND_SETPREF_LIST.equals(command) || COMMAND_SETPREF.equals(command) ||
                            (COMMAND_UNREAD + UNREAD_CONTACT_FLAG).equals(command)) {

                        //second line for contact name
                        String contactNameInput = inReader.readLine();
                        try {
                            if (contactNameInput.equals(NUMBER_FLAG)) {
                                // if number flag was given, don't retrieve contact, build one
                                // with the number
                                String number = inReader.readLine();
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
                                originalCommand = command;
                                // reply to the client requesting that user pick a preferred #
                                command = COMMAND_SETPREF_LIST;
                                //                              Log.d(TAG, "Replying: " + contactGetResult);
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
                        commandProcessor(command, originalCommand, contact, inReader, replyStream);
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
        String lenStr = "" + len;

        //if msg.length() >= 10^32, the stream will get stuck. this _probably_ won't happen.
        if (lenStr.length() < HEADER_LEN) {
            //right-pad the header with spaces
            lenStr = String.format("%1$-" + HEADER_LEN + "s", lenStr);
        }

        String response = lenStr + '\n' + msg;
        replyStream.println(response);
    }

    private void commandProcessor(String command, String originalCommand, Contact contact,
                                  BufferedReader inReader, PrintStream replyStream)
            throws IOException {
        if (COMMAND_SEND.equals(command)) {
            sendCommand(contact, inReader, replyStream);
        }
        else if (COMMAND_READ.equals(command)) {
            readCommand(contact, inReader, replyStream);
        }
        else if (COMMAND_UNREAD.equals(command)) {
            unreadCommand(inReader, replyStream);
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
            sendReply(replyStream, list);
        }
        else if (COMMAND_SETPREF.equals(command)) {
            // receive the index to set the new preferred number to.
            int replyIndex = Integer.parseInt(inReader.readLine());
            Log.d(TAG, "Setting " + contact.name() + " pref to " + replyIndex);
            contact.setPreferred(replyIndex);

            if (COMMAND_SEND.equals(originalCommand)) {
                sendCommand(contact, inReader, replyStream);
            }
            else if (COMMAND_READ.equals(originalCommand)) {
                readCommand(contact, inReader, replyStream);
            }
            else if (COMMAND_UNREAD.equals(originalCommand)) {
                unreadCommand(inReader, replyStream);
            }
            else {
                sendReply(replyStream, "Changed " + contact.name() + "'s preferred number to: " +
                        contact.preferred());
            }
        }
        else if (COMMAND_CONTACTS.equals(command)) {
            //TODO should accept tty width
            String allContacts = Utilities.getAllContacts();
            if (allContacts != null && !allContacts.isEmpty()) {
                sendReply(replyStream, allContacts);
            }
            else {
                sendReply(replyStream, "An error occurred getting contacts, " +
                        "or you have no contacts!");
            }
        }
        else {
            //should never happen
            sendReply(replyStream, "'" + command + "' is a not a recognized command. " +
                    "Please report this issue on GitHub.");
        }
    }

    private void sendCommand(Contact contact, BufferedReader inReader, PrintStream replyStream)
            throws IOException {
        StringBuilder msgBodyBuilder = new StringBuilder();
        //read the message body into msgBodyBuilder
        int current;
        char previous = ' ';
        while ((current = inReader.read()) != -1) {
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

        //chop off last newline
        messageInput = messageInput.substring(0, messageInput.length() - 1);
        try {
            Integer numberSent = (new SmsSendThread().execute(contact.preferred(), messageInput))
                    .get();

            sendReply(replyStream, "Successfully sent " + numberSent + " message" +
                    (numberSent != 1 ? "s" : "") + " to " +
                    contact.name() + ", " + contact.preferred() + ".");

            String preferred = "";
            if(!contact.name().equals(contact.preferred())) {
                // Don't display the preferred twice in the -n case.
                preferred = ", " + contact.preferred();
            }

            String reply = String.format(Locale.getDefault(), "Successfully sent %d message%s to %s%s.",
                    numberSent, numberSent != 1 ? "s" : "", contact.name(), preferred);

            sendReply(replyStream, reply);
        } catch (SecurityException e) {
            sendReply(replyStream, "No SMS permission! Open the " + getString(R.string.app_name) +
                    " app and give SMS permission.");
        } catch (Exception e) {
            sendReply(replyStream, "Unexpected exception in the SMS send " +
                    "thread; please report this issue on GitHub.");
            Log.e(TAG, "Exception from sendThread", e);
        }
    }

    private void readCommand(Contact contact, BufferedReader inReader, PrintStream replyStream)
            throws IOException {
        Log.d(TAG, "Reading from " + contact.preferred());
        int numberToRetrieve = Integer.parseInt(inReader.readLine());
        int outputWidth = Integer.parseInt(inReader.readLine());

        try {
            String convo = Utilities.getConversation(contact, numberToRetrieve,
                    outputWidth);

            if (convo != null) {
                sendReply(replyStream, convo);
                receiver.removeMessagesFromNumber(contact.preferred());
                Log.d(TAG, "Responded with convo with " + contact.name());
            }
            else {
                String response = "No messages found with " + contact.name();
                if (!contact.name().equals(contact.preferred())) {
                    // Don't display the preferred twice in the -n case.
                    response += ", " + contact.preferred() + ".";
                }
                sendReply(replyStream, response + '.');
            }

        } catch (SecurityException e) {
            sendReply(replyStream, "Could not retrieve messages: make sure " +
                    getString(R.string.app_name) + " has SMS permission.");
            Log.w(TAG, "No SMS permission for reading.");
        }
    }

    private void unreadCommand(BufferedReader inReader, PrintStream replyStream)
            throws IOException {
        int outputWidth = Integer.parseInt(inReader.readLine());
        try {
            String unread = receiver.getAllSms(outputWidth);

            if (unread != null && !unread.isEmpty()) {
                unread = "Unread Messages:\n" + unread;
                sendReply(replyStream, unread);
            }
            //            else if (contact != null)
            //                sendReply(replyStream, "No unread messages found with " +
            //                        contact.name() + ".");
            else {
                sendReply(replyStream, "No unread messages.");
            }
        } catch (SecurityException e) {
            sendReply(replyStream, "Could not retrieve messages: make sure " +
                    getString(R.string.app_name) + " has SMS permission.");
            Log.w(TAG, "No SMS permission for reading.");
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
/**
 * Data type class to hold info about a contact, and access preferred contact data when it's
 * required.
 */
class Contact {
    private static final String TAG = "Contact";

    private String name;
    // numbers includes the type, as returned from getNumberForContact
    private List<String> numbers;

    public Contact(String name, List<String> numbers) {
        this.name = name;
        this.numbers = numbers;
    }

    /**
     * Set the preferred number to the given one.
     *
     * @param number Number to set the contact's preferred number to.
     */
    public void setPreferred(String number) {
        boolean contains = false;
        for (String n : numbers) {
            if (numbers.size() == 1 || n.contains(number)) {
                contains = true;
                number = n;
            }
        }
        if (!contains) {
            // should not happen
            Log.e(TAG, "Invalid number " + number + " passed to setPreferred!!");
            return;
        }
        Context c = SmsServerService.instance().getApplicationContext();
        SharedPreferences sp = c.getSharedPreferences(c.getString(R.string.preferred_contacts_file),
                Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = sp.edit();
        edit.putString(c.getString(R.string.preferred_contacts_prefix) + name, number);
        Log.d(TAG, "Finished setting " + name + " pref to " + number);
        edit.apply();
    }

    /**
     * Used to set preferred using an index instead of a string number. Intended to be used when
     * getting setpref (NOT list) request from user.
     *
     * @param index Which index in numbers to set the new preferred to.
     */
    public void setPreferred(int index) {
        Log.d(TAG, "Setting " + name() + "'s preferred to " + numbers().get(index));
        setPreferred(numbers.get(index));
    }

    /**
     * Check the Shared Preferences for a preferred number.
     * ***** Could also check if the number is still in the phone book, and if not,
     * require a setpref request.
     *
     * @return The preferred number if it is in the preferences, null otherwise.
     */
    private String checkPrefs() {
        Context c = SmsServerService.instance().getApplicationContext();
        SharedPreferences prefs = c.getSharedPreferences(
                c.getString(R.string.preferred_contacts_file),
                Context.MODE_PRIVATE);
        return prefs.getString(c.getString(R.string.preferred_contacts_prefix) + name, null);
    }

    // Getters //
    public boolean hasPreferred() {
        return checkPrefs() != null;
    }

    public String preferred() {
        return checkPrefs();
    }

    public String name() {
        return name;
    }

    public List<String> numbers() {
        return numbers;
    }

    public int count() {
        return numbers.size();
    }
}