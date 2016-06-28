package ca.tetchel.shexter;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Public static utility methods to support the bulk of the code.
 */
public class Utilities {
    /**
     * Self documenting. Used to format phone numbers with +, -, spaces, etc.
     */
    public static String removeNonDigitCharacters(String input) {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if(Character.isDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Turns 1231231234 into 123-123-1234
     */
    public static String hyphenatePhoneNumber(String phoneNumber) {
        return phoneNumber.substring(0,3) + '-' + phoneNumber.substring(3, 6) + '-'
                + phoneNumber.substring(6);
    }

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
}
