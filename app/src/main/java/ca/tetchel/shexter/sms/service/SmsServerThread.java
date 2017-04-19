package ca.tetchel.shexter.sms.service;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.Scanner;

import ca.tetchel.shexter.R;
import ca.tetchel.shexter.sms.CommandProcessor;
import ca.tetchel.shexter.sms.Contact;
import ca.tetchel.shexter.sms.ServiceConstants;
import ca.tetchel.shexter.sms.Utilities;

import static android.content.Context.POWER_SERVICE;
import static ca.tetchel.shexter.sms.ServiceConstants.COMMAND_READ;
import static ca.tetchel.shexter.sms.ServiceConstants.COMMAND_SEND;
import static ca.tetchel.shexter.sms.ServiceConstants.COMMAND_SETPREF;
import static ca.tetchel.shexter.sms.ServiceConstants.COMMAND_SETPREF_LIST;
import static ca.tetchel.shexter.sms.ServiceConstants.COMMAND_UNREAD;
import static ca.tetchel.shexter.sms.ServiceConstants.NUMBER_FLAG;
import static ca.tetchel.shexter.sms.ServiceConstants.UNREAD_CONTACT_FLAG;

class SmsServerThread extends Thread {

    private static final String TAG = SmsServerThread.class.getSimpleName();

    private ServerSocket serverSocket;

    SmsServerThread(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    @Override
    public void run() {
        Log.d(TAG, "ServerThread starting up on port " + serverSocket.getLocalPort());

        // used to track previous command when setpref is used.
        String oldRequest = null;
        while (!Thread.currentThread().isInterrupted() && !serverSocket.isClosed()) {
            try {
                //region SetupServer
                Log.d(TAG, "Ready to accept");
                Socket socket = serverSocket.accept();
                // make sure phone stays awake - does this even work?
                Context context = SmsServerService.instance().getApplicationContext();
                PowerManager.WakeLock wakeLock = ((PowerManager) context
                        .getSystemService(POWER_SERVICE))
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                                context.getString(R.string.app_name));
                wakeLock.acquire();

                // print back to initSocket using this
                PrintStream replyStream = new PrintStream(socket.getOutputStream(), false,
                        ServiceConstants.ENCODING);
                //endregion
                // Store the full request
                Scanner scansAll;
                String request;
                try {
                    // Do not close this - it will close when the initSocket
                    // is closed in onDestroy
                    // TODO use a length header. This will break if message starts with newline!
                    scansAll = new Scanner(socket.getInputStream(), ServiceConstants.ENCODING)
                            .useDelimiter("\n\n");
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
                String contactError = null;
                Contact contact = null;
                // Determine if the command requires a contact.
                if (COMMAND_READ.equals(command) || COMMAND_SEND.equals(command) ||
                        COMMAND_SETPREF_LIST.equals(command) ||
                        COMMAND_SETPREF.equals(command) ||
                        (COMMAND_UNREAD + UNREAD_CONTACT_FLAG).equals(command)) {

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
                            contact = Utilities.getContactInfo(context.getContentResolver(),
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
                                context.getString(R.string.app_name) + " has Contacts permission.";
                    }
                }
                if (contactError != null) {
                    //if something has gone wrong already, don't do anything else
                    Log.d(TAG, "Sending error reply " + contactError);
                    Utilities.sendReply(replyStream, contactError);
                }
                //endregion
                else {
                    String response = CommandProcessor.process(command, oldRequest, contact,
                            requestReader);
                    Log.d(TAG, "Command successfully processed; replying.");
                    Utilities.sendReply(replyStream, response);
                }
                wakeLock.release();
            } catch (IOException e) {
                Log.e(TAG, "Socket error occurred.", e);
            }
        }

        Log.d(TAG, "Server loop exited. Interrupted? " + isInterrupted());
        if(serverSocket != null) {
            try {
                serverSocket.close();
                Log.d(TAG, "Closed ServerSocket");
            } catch (IOException e) {
                Log.e(TAG, "Exception closing ServerSocket in finally block", e);
            }
        }
    }
}