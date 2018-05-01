package ca.tetchel.shexter.eventlogger;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import ca.tetchel.shexter.R;
import ca.tetchel.shexter.main.MainActivity;

public class EventLogListAdapter extends ArrayAdapter<EventLogger.Event> {

    private static final String
            TAG = MainActivity.MASTER_TAG + EventLogListAdapter.class.getSimpleName();

    private final EventLogActivity activity;
    private final List<EventLogger.Event> events;

    public EventLogListAdapter(EventLogActivity activity, List<EventLogger.Event> events) {
        super(activity, R.layout.trusted_hosts_listitem, events);
        this.activity = activity;
        this.events = events;
    }

    @NonNull
    @Override
    public View getView(final int position, View view, @NonNull ViewGroup parent) {
        Log.d(TAG, "getview");

        LayoutInflater inflater = activity.getLayoutInflater();
        // TODO what is this warning?
        View rowView = inflater.inflate(R.layout.eventlog_listitem, null, true);

        final TextView eventTitleTV = rowView.findViewById(R.id.eventTitleTV);
        final TextView eventDetailTV = rowView.findViewById(R.id.eventDetailTV);
        final TextView eventDateTV = rowView.findViewById(R.id.eventDateTV);

        final EventLogger.Event current = events.get(position);
        eventTitleTV.setText(current.title);
        if(current.isError) {
            eventTitleTV.setTextColor(activity.getResources().getColor(R.color.colorError));
        }

        if(current.detail.isEmpty()) {
            eventDetailTV.setVisibility(View.GONE);
        }
        else {
            eventDetailTV.setText(current.detail);
            eventDetailTV.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    ClipboardManager clipboardManager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("EventLog Detail", current.detail);

                    if(clipboardManager != null) {
                        clipboardManager.setPrimaryClip(clip);
                        Toast.makeText(activity, activity.getString(R.string.copied),
                                Toast.LENGTH_SHORT).show();
                    }
                    else {
                        Toast.makeText(activity, activity.getString(R.string.failed_clipboard),
                                Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
            });
        }

        eventDateTV.setText(current.time24Hr);

        return rowView;
    }
}
