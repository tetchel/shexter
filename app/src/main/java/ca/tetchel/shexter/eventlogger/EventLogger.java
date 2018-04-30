package ca.tetchel.shexter.eventlogger;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import ca.tetchel.shexter.sms.ShexterService;

public class EventLogger {

    private static final int MAX_SIZE = 1000;

    private static List<Event> events = new ArrayList<>();

    public static List<Event> getEvents() {
        if(events.isEmpty()) {
            events = loadEvents();
            // EventLogger.logError("Test Exception", new Exception("lalala"));
            // Might still be empty!
        }
        return events;
    }

    public static void clearEvents() {
        events.clear();
        onAnyEvent();
    }

    static class Event {
        public final String title;
        public final String detail;

        // YAGNI
        // public final Date datetime;
        public final String time24Hr;

        public final boolean isError;

        @SuppressLint("SimpleDateFormat")
        private static final SimpleDateFormat DF_24Hr = new SimpleDateFormat("HH:mm:ss");

        private Event(String title, String detail, boolean isError) {
            this.title = title;
            this.detail = detail;
            this.isError = isError;

            // this.datetime = Calendar.getInstance().getTime();
            this.time24Hr = get24HrTime(Calendar.getInstance().getTime());
        }

        /**
         * To be called by the eventlog loader
         */
        private Event(String title, String detail, String time24Hr, boolean isError) {
            this.title = title;
            this.detail = detail;
            this.isError = isError;
            this.time24Hr = time24Hr;
        }

        private static String get24HrTime(Date date) {
            return DF_24Hr.format(date);
        }
    }

    public static void log(String title) {
        events.add(new Event(title, "", false));
        onAnyEvent();
    }

    public static void log(String title, String detail) {
        events.add(new Event(title, detail, false));
        onAnyEvent();
    }

    public static void logError(String title) {
        events.add(new Event(title, "", true));
        onAnyEvent();
    }

    public static void logError(String title, String detail) {
        events.add(new Event(title, detail, true));
        onAnyEvent();
    }

    public static void logError(String title, Exception e) {
        events.add(new Event(title, getStackTraceAsString(e), true));
        onAnyEvent();
    }

    public static void logError(Exception e) {
        events.add(new Event("Error occurred!", getStackTraceAsString(e), true));
        onAnyEvent();
    }

    private static String getStackTraceAsString(Exception e) {
        if(e == null) {
            return "null exception";
        }

        StringBuilder resultBuilder = new StringBuilder();
        for (StackTraceElement ste : e.getStackTrace()) {
            // Filter by pertinent method calls only
            if(ste.getClassName().startsWith("ca.tetchel.shexter")) {
                resultBuilder.append(ste.toString()).append('\n');
            }
        }
        String result = resultBuilder.toString();
        // remove final newline
        result = result.substring(0, result.length() - 1);
        return result;
    }

    /////   Saving and Loading code   /////
    private static final String
            TAG = EventLogger.class.getSimpleName(),
            PREFSKEY = "eventlogger-prefs",
            DELIMITER = "----- EventEnd ----";

    private static final int EXPECTED_NO_FIELDS = 4;

    /**
     * Call this to write the event log to memory after any event is logged.
     */
    private static void onAnyEvent() {
        while(events.size() > MAX_SIZE) {
            // Remove oldest event
            events.remove(0);
        }

        SharedPreferences.Editor editor = ShexterService.instance().getApplicationContext()
                .getSharedPreferences(PREFSKEY, Context.MODE_PRIVATE)
                .edit();

        StringBuilder logBuilder = new StringBuilder();
        for(Event event : events) {
            String eventStr = String.format("%s\n%s\n%s\n%b",
                    event.title, event.detail, event.time24Hr, event.isError);
            logBuilder.append(eventStr).append(DELIMITER);
        }
        Log.d(TAG, "Saving event log: " + logBuilder.toString());
        editor.putString(PREFSKEY, logBuilder.toString());
        editor.apply();
    }

    private static List<Event> loadEvents() {
        List<Event> events = new ArrayList<>();

        SharedPreferences sp = ShexterService.instance().getApplicationContext()
                .getSharedPreferences(PREFSKEY, Context.MODE_PRIVATE);
        String eventsPref = sp.getString(PREFSKEY, "");

        if(!eventsPref.isEmpty()) {
            for(String eventStr : eventsPref.split(DELIMITER)) {
                String[] lines = eventStr.split("\n");
                if(lines.length < EXPECTED_NO_FIELDS) {
                    Log.w(TAG, "Received malformed event " + eventStr);
                    continue;
                }
                String  title = lines[0],
                        detail = lines[1],
                        time24Hr = lines[2];

                boolean isError = Boolean.parseBoolean(lines[lines.length-1]);
                Log.d(TAG, "Loaded event " + title + " isError " + isError);
                events.add(new Event(title, detail, time24Hr, isError));
            }
        }
        return events;
    }
}
