package ca.tetchel.shexter.sms;

public class ServiceConstants {

   public static final int  PORT_MIN = 23456,
                            PORT_MAX = 23461,
                            // Responses start with a length header of this many bytes
                            // so the client knows how much to recv
                            LENGTH_HEADER_LEN = 32;

   public static final String ENCODING = "UTF-8";

   // Commands and related flags

   // Command constants
   public static final String
            COMMAND_SEND = "send",
            COMMAND_READ = "read",
            COMMAND_UNREAD = "unread",
            COMMAND_SETPREF = "setpref",
   // flag for a lone setpref request (ie, not one required by another command)
           COMMAND_SETPREF_LIST = COMMAND_SETPREF + "-list",
            UNREAD_CONTACT_FLAG = "-contact",
            NUMBER_FLAG = "-number";

   static final String
            // flag to send back to the client when setpref required
            SETPREF_REQUIRED = "NEED-SETPREF",
            COMMAND_CONTACTS = "contacts";
}
