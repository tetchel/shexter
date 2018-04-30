package ca.tetchel.shexter.sms.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import ca.tetchel.shexter.BuildConfig;
import ca.tetchel.shexter.eventlogger.EventLogger;
import ca.tetchel.shexter.main.MainActivity;
import ca.tetchel.shexter.sms.ShexterService;

/**
 * static utility methods to support the SMS code.
 */
public class SmsUtilities {

    private static final String
            TAG = MainActivity.MASTER_TAG + SmsUtilities.class.getSimpleName();

    static String formatSms(String sender, String otherSender, String body, long time,
                                   int width) {
        StringBuilder messageBuilder = new StringBuilder();

        String niceTime = '[' +  SmsUtilities.unixTimeToTime(time) + "] ";
        messageBuilder.append(niceTime);
        messageBuilder.append(sender);

        //Indent the messages the same length as their prefix so they don't look weird
        int firstLineIndent = niceTime.length() + sender.length();
        int amountToIndent = niceTime.length() + Math.max(sender.length(), otherSender.length());
        // Shift over any newlines in the body so that they line up with the sender's name
        String indent = new String(new char[amountToIndent]).replace("\0", " ");
        body = body.replaceAll("\n", "\n"+indent);

        //keep lines shorter than LINE_LEN chars.
        int remainingLineChars = width - amountToIndent;
        List<String> divided = divideString(body, remainingLineChars);
        for(int i = 0; i < divided.size(); i++) {
            //shift the message body over for nicer-looking output
            if(i != 0) {
                int numberOfSpacesToWrite = amountToIndent;
                //if it starts with a space shift it over to the left
                if(divided.get(i).startsWith(" ")) {
                    numberOfSpacesToWrite--;
                }
                for(int j = 0; j < numberOfSpacesToWrite; j++) {
                    messageBuilder.append(' ');
                }
            }
            //if it's the first line of the message and it needs more indentation, do it
            else if(amountToIndent > firstLineIndent) {
                int numberOfSpacesToWrite = amountToIndent - firstLineIndent;
                for(int j = 0; j < numberOfSpacesToWrite; j++) {
                    messageBuilder.append(' ');
                }
            }
            messageBuilder.append(divided.get(i));
        }
        return messageBuilder.toString();
    }

    static String messagesIntoOutput(List<String> messages, List<Long> dates) {
        //combine the messages into one string to be sent to the client
        StringBuilder resultBuilder = new StringBuilder();
        //Generate and print a new date header each time the next message is from a different day
        String lastUsedDate = null;
        for(int i = 0; i < messages.size(); i++) {
            String currentDate = SmsUtilities.unixTimeToRelativeDate(dates.get(i));

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
     * Divides a String into a List of substrings of the length subStringLength.
     */
    private static List<String> divideString(String input, int subStringLength) {
        List<String> result = new ArrayList<>((input.length() / subStringLength) + 1);
        int currentIndex = 0;
        while(currentIndex < input.length()) {
            int remaining = input.length() - currentIndex;
            String subSection;
            //is there room on the line for the rest of the message?
            if(remaining < subStringLength) {
                subSection = input.substring(currentIndex);
            }
            else {
                subSection = input.substring(currentIndex, currentIndex + subStringLength);
            }
            currentIndex += subSection.length();
            result.add(subSection);
        }
        return result;
    }

    ////////// Database Querying methods //////////

    static String getConversation(ContentResolver contentResolver, Contact contact,
                                  int numberToRetrieve, int outputWidth)
            throws SecurityException {
        if(BuildConfig.DEBUG && !(contact != null && contact.count() != 0))
            throw new RuntimeException("Invalid data passed to getConversation: contact is null? " +
                    (contact == null));

        Uri uri = Uri.parse("content://sms/");
        final String[] projection = new String[]{"date", "body", "type", "address", "read"};

        Cursor query = contentResolver.query(uri, projection, null, null, "date desc");
        //Log.d(TAG, "Query selection is " + selection);

        if(query == null) {
            Log.e(TAG, "Null Cursor trying to get conversation with " + contact.name() + ", # " +
                    contact.preferred());
            return null;
        }
        else if(query.getCount() == 0) {
            Log.e(TAG, "No result trying to get conversation with " + contact.name() + ", # " +
                    contact.preferred());

            query.close();
            return null;
        }

        int count = 0;
        List<String> messages = new ArrayList<>(numberToRetrieve);
        List<Long> dates = new ArrayList<>(numberToRetrieve);

        //this will succeed because already checked query's count
        query.moveToFirst();
        int dateCol = query.getColumnIndex("date");
        int bodyCol = query.getColumnIndex("body");
        int typeCol = query.getColumnIndex("type");
        int addrCol = query.getColumnIndex("address");

//        Log.d(TAG, "Successful sms query for " + contactInfo[1] + ", address is " +
//                query.getString(query.getColumnIndex("address")));

        String preferred = contact.preferred();
        do {
            String addr = query.getString(addrCol);
            //Skip all texts that aren't from the requested contact, if one was given.
            if(!PhoneNumberUtils.compare(addr, preferred))
                continue;

            String body = query.getString(bodyCol);
            int type = query.getInt(typeCol);
            long time = query.getLong(dateCol);

            //add sender to the message
            final String YOU = "You", SENDER_SUFFIX = ": ";
            String sender = YOU;
            String otherSender = contact.name();
            if(type == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX) {
                sender = contact.name();
                otherSender = YOU;
            }
            sender += SENDER_SUFFIX;
            otherSender += SENDER_SUFFIX;

            String message = SmsUtilities.formatSms(sender, otherSender, body, time, outputWidth);

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

        return SmsUtilities.messagesIntoOutput(messages, dates);
    }

    /**
     * Accepts contact name (case insensitive), returns:
     * @return Contact data object with the contact's name and numbers.
     */
    public static Contact getContactInfo(Context context, String name)
            throws SecurityException {
        String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME +
                " like'%" + name + "%'";
        String[] projection = new String[] {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.Contacts._ID,};

        ContentResolver contentResolver = context.getContentResolver();
        Cursor query;
        try {
            query = contentResolver.query(ContactsContract.Contacts.CONTENT_URI,
                    projection, selection, null, null);
        }
        catch (SecurityException e) {
            Log.w(TAG, "No 'Contacts' permission, cannot proceed.");
            EventLogger.logError(context, e);
            throw(e);
        }

        Contact result;
        try {
            if (query != null) {
                int nameCol = query.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int idCol = query.getColumnIndex(ContactsContract.Contacts._ID );
                if (query.moveToFirst()) {
                    List<String> numbers = getNumbersForContact(contentResolver,
                            query.getLong(idCol));
                    result = new Contact(query.getString(nameCol), numbers);
                } else {
                    Log.w(TAG, "No result for getting phone number of " + name);
                    return null;
                }
                while(query.moveToNext()) {
//                    Log.d(TAG, "Found another contact similar to " + name + ": " +
//                            c.getString(nameIndex));
                    //The above code selects 'mom old' over 'mom'. This is to fix that issue -
                    //it guarantees that if you enter the exact correct name you will get the
                    //contact every time.
                    if(result.numbers().isEmpty() ||
                            query.getString(nameCol).equalsIgnoreCase(name)) {
                        List<String> numbers = getNumbersForContact(contentResolver,
                                query.getLong(idCol));
                        //make sure the contact has numbers associated with them, otherwise
                        //this is a waste. this is to stop from selecting email contacts etc
                        if(!numbers.isEmpty())
                            result = new Contact(query.getString(nameCol), numbers);
                    }
                }
            }
            else {
                Log.e(TAG, "Received nonexistent contact " + name);
                return null;
            }
        }
        finally {
            if(query != null)
                query.close();
        }
        return result;
    }

    /**
     * Gets all contacts the user has stored and returns a list of them with all their numbers.
     * @return A formatted string
     * @throws SecurityException If there's no Contacts permission.
     */
    static String getAllContacts(Context context) throws SecurityException {
        // could be expanded to start with an input / match a regex
        String[] projection = new String[]{
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.HAS_PHONE_NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME };

        ContentResolver contentResolver = context.getContentResolver();
        Cursor query;
        try {
            query = contentResolver.query(ContactsContract.Contacts.CONTENT_URI,
                    projection, null, null,
                    "upper(" + ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + ") ASC");
        }
        catch (SecurityException e) {
            Log.w(TAG, "No 'Contacts' permission, cannot proceed.");
            EventLogger.logError(context, e);
            throw(e);
        }

        StringBuilder contactsBuilder = new StringBuilder();
        try {
            if (query != null) {
                if(query.moveToFirst()) {
                    int hasNumberIndex = query.getColumnIndex(
                            ContactsContract.Contacts.HAS_PHONE_NUMBER);
                    int nameIndex = query.getColumnIndex(
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                    int idIndex = query.getColumnIndex(ContactsContract.Contacts._ID);
                    do {
                        long id = query.getLong(idIndex);
                        String name = query.getString(nameIndex);
                        if (query.getInt(hasNumberIndex) > 0) {
                            List<String> numbers = getNumbersForContact(contentResolver, id);
                            for(String s : numbers) {
                                contactsBuilder.append(name).append(": ").append(s).append('\n');
                            }
                        }

                    } while (query.moveToNext());
                }
                else {
                    Log.w(TAG, "Empty cursor when getting all contacts!");
                    return null;
                }
            }
            else {
                Log.e(TAG, "Null cursor when getting all contacts!");
                return null;
            }
        }
        finally {
            if(query != null)
                query.close();
        }
        return contactsBuilder.toString();
    }

    /**
     * Gets all the numbers for a given contact.
     * @param contactId ContactsContact.Contacts._ID of the contact to get numbers for.
     * @return List of numbers, each in the form $type: $number
     */
    private static List<String> getNumbersForContact(ContentResolver contentResolver,
                                                     long contactId) {
        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] projection = { ContactsContract.CommonDataKinds.Phone.NUMBER,
                                ContactsContract.CommonDataKinds.Phone.TYPE };

        Cursor numbersQuery = contentResolver.query(uri, projection,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID+ " = ?",
                new String[] { ""+contactId }, null);

        List<String> numbers = new ArrayList<>();
        if (numbersQuery == null) {
            Log.w(TAG, "Null result getting numbers for " + contactId);
            return numbers;
        }
        try {
            if (!numbersQuery.moveToFirst()) {
                Log.d(TAG, "No numbers for contact #" + contactId);
                // will return empty list
            }
            else {
                int numberCol = numbersQuery.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.NUMBER);
                int typeCol = numbersQuery.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.TYPE);

                do {
                    String number = numbersQuery.getString(numberCol);
                    //normalize the numbers
//                    if (Build.VERSION.SDK_INT >= 21)
//                        number = PhoneNumberUtils
//                                .formatNumber(number, Locale.getDefault().getISO3Country());
//                    else
//                        number = PhoneNumberUtils.formatNumber(number);
                    int typeInt = numbersQuery.getInt(typeCol);
                    String type = ContactsContract.CommonDataKinds.Phone
                            .getTypeLabel(ShexterService.instance().getResources(), typeInt, "")
                                    .toString();
                    numbers.add(type + ": " + number);
                } while (numbersQuery.moveToNext());
                numbersQuery.close();
            }
        }
        finally {
            numbersQuery.close();
        }
        return numbers;
    }

    ////////// String/Date SmsUtilities //////////

    /**
     * Determine how old the message is and return a date label depending on the current date.
     * @param unixTime Unix time in milliseconds to be converted to date label.
     * @return Date label depending on how long ago the date was - eg Today, Yesterday,
     * Day of Week (if date is in the past week), Month/Day (if date is in the current year),
     * Full date otherwise
     */
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

    /**
     * The java.util.Date api really sucks. Compares two dates, ignoring their time-of-day fields.
     * Horrifying.
     */
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

    /**
     * Returns unix time as HH:mm format.
     */
    private static String unixTimeToTime(long unixTime) {
        Date d = new Date(unixTime);
        DateFormat df = new SimpleDateFormat("HH:mm", Locale.getDefault());
        df.setTimeZone(TimeZone.getDefault());

        return df.format(d);
    }

    private static String rightPad(String str, int desiredLength) {
        return String.format("%1$-" + desiredLength + "s", str);
    }

    /**
     * Wrapper for printStream.println which sends a length header followed by \n before the body
     * to make it easier for the client to properly receive all data.
     *
     * @param replyStream Stream to print message to.
     * @param msg         Message to send.
     */
    public static void sendReply(PrintStream replyStream, String msg) {
        int len = msg.length();

        String header = Integer.toString(len);

        //if msg.length() >= 10^32, the stream will get stuck. this _probably_ won't happen.
        if (header.length() < ServiceConstants.LENGTH_HEADER_LEN) {
            header = SmsUtilities.rightPad(header, ServiceConstants.LENGTH_HEADER_LEN);
        }

        Log.d(TAG, "Sending with header: " + header);
        String response = header + '\n' + msg;
        replyStream.println(response);
    }

}
