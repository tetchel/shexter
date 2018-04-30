package ca.tetchel.shexter.eventlogger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class EventLogger {

    private static List<Event> events = new ArrayList<>();

    public static List<Event> getEvents() { return events; }

    static class Event {
        public final String title;
        public final String detail;
        public final Date datetime;

        public final boolean isError;

        private static final SimpleDateFormat DF_24Hr = new SimpleDateFormat("HH:mm:ss");

        private Event(String title, String detail, boolean isError) {
            this.title = title;
            this.detail = detail;
            this.isError = isError;

            datetime = Calendar.getInstance().getTime();
        }

        private Event(String title, Exception e) {
            this(title, getStackTraceAsString(e), true);
        }

        public static String get24HrTime(Date date) {
            return DF_24Hr.format(date);
        }
    }

    public static void log(String title, String detail) {
        events.add(new Event(title, detail, false));
    }

    public static void logError(String title, String detail) {
        events.add(new Event(title, detail, true));
    }

    public static void logError(String title, String detail, Exception e) {
        events.add(new Event(title, e));
    }

    private static String getStackTraceAsString(Exception e) {
        if(e == null) {
            return "null exception";
        }

        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
