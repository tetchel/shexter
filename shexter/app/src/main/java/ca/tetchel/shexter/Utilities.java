package ca.tetchel.shexter;

import android.provider.Telephony;
import android.support.annotation.NonNull;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Public static utility methods to support the bulk of the code.
 */
public class Utilities {

    /**
     * Self documenting. Used to format phone numbers with +, -, spaces, etc.
     */
    /*
    public static String removeNonDigitCharacters(String input) {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if(Character.isDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }*/

    /**
     * Turns 1231231234 into 123-123-1234
     */
    /*
    public static String hyphenatePhoneNumber(String phoneNumber) {
        return phoneNumber.substring(0, 3) + '-' + phoneNumber.substring(3, 6) + '-'
                + phoneNumber.substring(6);
    }*/

    /**
     * Determine how old the message is and return a date label depending on the current date.
     * @param unixTime Unix time in milliseconds to be converted to date label.
     * @return Date label depending on how long ago the date was - eg Today, Yesterday,
     * Day of Week (if date is in the past week), Month/Day (if date is in the current year),
     * Full date otherwise
     */
    public static String unixTimeToRelativeDate(long unixTime) {
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
     * Horrifying for such a simple thing.
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
    public static String unixTimeToTime(long unixTime) {
        Date d = new Date(unixTime);
        DateFormat df = new SimpleDateFormat("HH:mm", Locale.getDefault());
        df.setTimeZone(TimeZone.getDefault());

        return df.format(d);
    }

    public static String formatSms(String sender, String otherSender, String body, long time,
                                   int width) {
        StringBuilder messageBuilder = new StringBuilder();

        //a left/right conversation display like real texting would be stellar!
        String niceTime = '[' +  Utilities.unixTimeToTime(time) + "] ";
        messageBuilder.append(niceTime);

        messageBuilder.append(sender);

        //Indent the messages the same length as their prefix so they don't look weird
        int firstLineIndent = niceTime.length() + sender.length();
        int amountToIndent = niceTime.length() + Math.max(sender.length(), otherSender.length());
        //keep lines shorter than LINE_LEN chars.
        int remainingLineChars = width - amountToIndent;
        List<String> divided = divideString(body, remainingLineChars);
        for(int i = 0; i < divided.size(); i++) {
            //shift the message body over for nicer-looking output
            //(IMO. up for discussion).
            if(i != 0) {
                int numberOfSpacesToWrite = amountToIndent;
                //if it starts with a space shift it over to the left
                if(divided.get(i).startsWith(" "))
                    numberOfSpacesToWrite--;
                for(int j = 0; j < numberOfSpacesToWrite; j++)
                    messageBuilder.append(' ');
            }
            //if it's the first line of the message and it needs more indentation, do it
            else if(amountToIndent > firstLineIndent) {
                int numberOfSpacesToWrite = amountToIndent - firstLineIndent;
                for(int j = 0; j < numberOfSpacesToWrite; j++)
                    messageBuilder.append(' ');
            }
            messageBuilder.append(divided.get(i));
        }
        return messageBuilder.toString();
    }

    @NonNull
    public static String messagesIntoOutput(List<String> messages, List<Long> dates) {
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
     * Divides a String into a List of substrings of the length subStringLength.
     */
    public static List<String> divideString(String input, int subStringLength) {
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
}
