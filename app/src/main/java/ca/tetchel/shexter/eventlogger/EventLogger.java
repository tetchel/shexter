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

public class EventLogger {

    private static final int MAX_SIZE = 1000;

    private static boolean hasLoadedEvents = false;
    private static List<Event> events = new ArrayList<>();

    public static List<Event> getEvents(Context context) {
        Log.d(TAG, "GetEvents");
        loadEvents(context);
        // EventLogger.logError("Test Exception", new Exception("lalala"));
        return events;
    }

    public static void clearEvents(Context context) {
        Log.d(TAG, "Clearing EventLog");
        events.clear();
        writeEvents(context);
    }

    static class Event {
        public final String title;
        public final String detail;

        // public final Date datetime;
        public final String timestamp;

        public final boolean isError;

        @SuppressLint("SimpleDateFormat")
        private static final SimpleDateFormat DATEFORMAT = new SimpleDateFormat("MM/dd HH:mm:ss");

        private Event(String title, String detail, boolean isError) {
            this.title = title;
            this.detail = detail;
            this.isError = isError;

            // this.datetime = Calendar.getInstance().getTime();
            this.timestamp = DATEFORMAT.format(Calendar.getInstance().getTime());
            checkValid();
        }

        /**
         * To be called by the eventlog loader
         */
        private Event(String title, String detail, String time24Hr, boolean isError) {
            this.title = title;
            this.detail = detail;
            this.timestamp = time24Hr;
            this.isError = isError;
            checkValid();
        }

        /**
         * Only the 'detail' field may contain newlines
         */
        private void checkValid() {
            String forbidden = "\n";
            if(title.contains(forbidden) || timestamp.contains(forbidden)) {
                Log.e(TAG, "Invalid event: " + toString());
            }
        }

        @Override
        public String toString() {
            return String.format("Event isError: %b at %s - Title: %s, Detail: %s", isError, timestamp, title, detail);
        }
    }

    public static void log(Context context, String title) {
        newEvent(context, new Event(title, "", false));
    }

    public static void log(Context context, String title, String detail) {
        newEvent(context, new Event(title, detail, false));
    }

    public static void logError(Context context, String title) {
        newEvent(context, new Event(title, "", true));
    }

    public static void logError(Context context, String title, String detail) {
        newEvent(context, new Event(title, detail, true));
    }

    public static void logError(Context context, String title, Exception e) {
        newEvent(context, new Event(title, getStackTraceAsString(e), true));
    }

    public static void logError(Context context, Exception e) {
        newEvent(context, new Event("Error", getStackTraceAsString(e), true));
    }

    private static void newEvent(Context context, Event event) {
        events.add(event);
        writeEvents(context);
    }

    private static String getStackTraceAsString(Exception e) {
        if(e == null) {
            return "null exception";
        }

        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append(e.toString()).append('\n');

        for (StackTraceElement ste : e.getStackTrace()) {
            // Filter by pertinent method calls only
            if(ste.getClassName().startsWith("ca.tetchel")) {
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
            DELIMITER = "-_EventEnd_-\n";

    private static final int EXPECTED_NO_FIELDS = 4;

    /**
     * Call this to write the event log to sharedprefs after any event is logged.
     */
    private static void writeEvents(Context context) {
        Log.d(TAG, "WriteEvents");
        if(!hasLoadedEvents) {
            loadEvents(context);
            hasLoadedEvents = true;
        }

        while(events.size() > MAX_SIZE) {
            // Remove oldest event
            events.remove(0);
            Log.d(TAG, "Too many events; deleted one");
        }

        SharedPreferences.Editor editor = context.getSharedPreferences(PREFSKEY, Context.MODE_PRIVATE).edit();

        StringBuilder logBuilder = new StringBuilder();
        for(Event event : events) {
            String eventStr = String.format("%s\n%s\n%b\n%s",
                    event.title, event.timestamp, event.isError, event.detail);
            logBuilder.append(eventStr).append(DELIMITER);
        }
        Log.d(TAG, "Saving event log: \"" + logBuilder.toString() + "\"");
        editor.putString(PREFSKEY, logBuilder.toString());
        editor.apply();
    }

    private static void loadEvents(Context context) {
        Log.d(TAG, "LoadEvents");
        if(hasLoadedEvents) {
            Log.d(TAG, "Already loaded events");
            return;
        }

        Log.d(TAG, "Haven't loaded events, doing it now");
        hasLoadedEvents = true;
        events = new ArrayList<>();

        SharedPreferences sp = context.getSharedPreferences(PREFSKEY, Context.MODE_PRIVATE);
        String eventsPref = sp.getString(PREFSKEY, "");

        if(eventsPref.isEmpty()) {
            Log.d(TAG, "Loaded empty EventLog");
            return;
        }

        for(String eventStr : eventsPref.split(DELIMITER)) {
            String[] lines = eventStr.split("\n");
            if(lines.length < EXPECTED_NO_FIELDS) {
                Log.w(TAG, "Received malformed event " + eventStr);
                continue;
            }
            String  title = lines[0],
                    time24Hr = lines[1];
            boolean isError = Boolean.parseBoolean(lines[2]);

            // the details field is the rest of the eventStr
            StringBuilder detailBuilder = new StringBuilder();
            for(int i = 3; i < lines.length; i++) {
                detailBuilder.append(lines[i]).append('\n');
            }

            Event e = new Event(title, detailBuilder.toString(), time24Hr, isError);
            Log.d(TAG, "Loaded event " + e.toString());
            events.add(e);
        }
    }
}
