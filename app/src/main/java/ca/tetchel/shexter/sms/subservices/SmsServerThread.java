package ca.tetchel.shexter.sms.subservices;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.Scanner;

import ca.tetchel.shexter.R;
import ca.tetchel.shexter.main.MainActivity;
import ca.tetchel.shexter.sms.ShexterService;
import ca.tetchel.shexter.sms.util.CommandProcessor;
import ca.tetchel.shexter.sms.util.Contact;
import ca.tetchel.shexter.sms.util.ServiceConstants;
import ca.tetchel.shexter.sms.util.SmsUtilities;
import ca.tetchel.shexter.trust.TrustedHostsUtilities;

import static android.content.Context.POWER_SERVICE;
import static ca.tetchel.shexter.sms.util.ServiceConstants.COMMAND_READ;
import static ca.tetchel.shexter.sms.util.ServiceConstants.COMMAND_SEND;
import static ca.tetchel.shexter.sms.util.ServiceConstants.COMMAND_SEND_INITIALIZER;
import static ca.tetchel.shexter.sms.util.ServiceConstants.COMMAND_SETPREF;
import static ca.tetchel.shexter.sms.util.ServiceConstants.COMMAND_SETPREF_LIST;
import static ca.tetchel.shexter.sms.util.ServiceConstants.COMMAND_UNREAD;
import static ca.tetchel.shexter.sms.util.ServiceConstants.NUMBER_FLAG;
import static ca.tetchel.shexter.sms.util.ServiceConstants.UNREAD_CONTACT_FLAG;

public class SmsServerThread extends Thread {

    private static final String
            TAG = MainActivity.MASTER_TAG + SmsServerThread.class.getSimpleName();

    private ServerSocket serverSocket;

    public SmsServerThread(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    private String readFullRequest(InputStream requestStream) {
        Scanner scansAll;
        String request;
        // TODO use a length header. This will break if message starts with newline!
        scansAll = new Scanner(requestStream, ServiceConstants.ENCODING)
                .useDelimiter("\n\n");
        request = scansAll.hasNext() ? scansAll.next() : "";
        if(request.isEmpty()) {
            Log.e(TAG, "Could not read request!");
        }

        return request;
    }

    private boolean commandRequiresContact(String command) {
        return  COMMAND_READ.equals(command) ||
                COMMAND_SEND.equals(command) ||
                COMMAND_SEND_INITIALIZER.equals(command) ||
                COMMAND_SETPREF_LIST.equals(command) ||
                COMMAND_SETPREF.equals(command) ||
                (COMMAND_UNREAD + UNREAD_CONTACT_FLAG).equals(command);
    }

    @Override
    public void run() {
        Log.d(TAG, "ServerThread starting up on port " + serverSocket.getLocalPort());

        // used to track previous command when setpref is used.
        String oldRequest = null;
        while (!Thread.currentThread().isInterrupted() && !serverSocket.isClosed()) {
            try {
                
                // make sure phone stays awake - does this even work?
                Context appContext = ShexterService.instance().getApplicationContext();
                PowerManager powerManager = ((PowerManager) appContext
                        .getSystemService(POWER_SERVICE));

                PowerManager.WakeLock wakeLock = null;
                if(powerManager != null) {
                     wakeLock = powerManager
                            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                                    appContext.getString(R.string.app_name));
                    wakeLock.acquire(5*60*1000);
                }
                else {
                    Log.e(TAG, "PowerManager was null, could not acquire wakelock");
                }

                Log.d(TAG, "Ready to accept");
                Socket socket = serverSocket.accept();

                // print back to client socket using this
                PrintStream replyStream = new PrintStream(socket.getOutputStream(), false,
                        ServiceConstants.ENCODING);

                InetAddress other = socket.getInetAddress();
                Log.d(TAG, "Accepted connection from " + other);

                if(!TrustedHostsUtilities.isHostTrusted(appContext, socket.getInetAddress())) {
                    Log.i(TAG, "Rejected request from " + socket.getInetAddress());
                    SmsUtilities.sendReply(replyStream,
                            "Your phone was found, but " + appContext.getString(R.string.app_name) +
                                    " rejected your request. " +
                                    "Approve the connection using the notification on your phone");
                    continue;
                }

                String request = readFullRequest(socket.getInputStream());
                if(request.isEmpty()) {
                    Log.e(TAG, "Received empty request!");
                    continue;
                }
                
                // At this point, the user has approved the other host, so we proceed with
                // command parsing and execution.
                BufferedReader requestReader = new BufferedReader(new StringReader(request));

                //The request protocol is command\ncontact\ncommand-specific-data
                //so read the first line to get the command
                String command = requestReader.readLine();
                Log.d(TAG, "Received command: " + command);

                
                String contactError = null;
                Contact contact = null;

                if (commandRequiresContact(command)) {

                    Log.d(TAG, "Getting contact name");
                    //second line for contact name
                    String contactNameInput = requestReader.readLine();
                    try {
                        if (contactNameInput.equals(NUMBER_FLAG)) {
                            Log.d(TAG, "Number flag was given");
                            // if number flag was given, don't retrieve contact, build one
                            // with the number
                            String number = requestReader.readLine();
                            contact = new Contact(number, Collections.singletonList(number));
                        }
                        else {
                            // get the contact's info from name
                            contact = SmsUtilities.getContactInfo(appContext.getContentResolver(),
                                    contactNameInput);
                        }

                        if (contact == null) {
                            //non existent contact
                            contactError = "Couldn't find contact '" + contactNameInput +
                                    "' with any associated phone numbers, please make sure the "
                                    + "contact exists and is spelled correctly.";
                        }
                        // Validate the contact / do any additional work
                        else if (contact.count() == 0) {
                            contactError = "You have no phone number associated with " +
                                    contact.name() + ".";
                        }
                        else if (contact.count() == 1 && !contact.hasPreferred()) {
                            Log.d(TAG, "Contact had 1 number.");
                            //will automatically pick the first/only number
                            contact.setPreferred(0);
                        }
                        else if (contact.count() > 1 && !contact.hasPreferred()
                                && !command.equals(COMMAND_SETPREF)) {
                            Log.d(TAG, "SetPref is required.");
                            // reply to the client requesting that user pick a preferred #
                            oldRequest = request;
                            command = COMMAND_SETPREF_LIST;
                        }
                    } catch (SecurityException e) {
                        contactError = "Could not retrieve contact info: make sure " +
                                appContext.getString(R.string.app_name) + " has Contacts permission.";
                    }
                }

                // At this point, we check contactError to see if we can proceed

                if (contactError != null) {
                    //if something has gone wrong already, don't do anything else
                    Log.d(TAG, "Sending error reply " + contactError);
                    SmsUtilities.sendReply(replyStream, contactError);
                }
                else {
                    // Everything went OK and we can continue to execute the user's command.

                    String response = CommandProcessor.process(command, oldRequest, contact,
                            requestReader);
                    Log.d(TAG, "Command successfully processed; replying: " + response);
                    SmsUtilities.sendReply(replyStream, response);
                }
                if(wakeLock != null) {
                    wakeLock.release();
                }
            }
            catch (IOException e) {
                Log.e(TAG, "Socket error occurred.", e);
            }
        }

        Log.d(TAG, "Server loop exited. Interrupted? " + isInterrupted());
        if (serverSocket != null) {
            try {
                serverSocket.close();
                Log.d(TAG, "Closed ServerSocket");
            } catch (IOException e) {
                Log.e(TAG, "Exception closing ServerSocket in finally block", e);
            }
        }
    }
}