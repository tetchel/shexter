package ca.tetchel.shexter.eventlogger;

import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

import ca.tetchel.shexter.R;
import ca.tetchel.shexter.main.MainActivity;
import ca.tetchel.shexter.trust.TrustedHostsListAdapter;

public class EventLogListAdapter extends ArrayAdapter<EventLogger.Event> {

    private static final String
            TAG = MainActivity.MASTER_TAG + TrustedHostsListAdapter.class.getSimpleName();

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
        View rowView = inflater.inflate(R.layout.trusted_hosts_listitem, null, true);
        final TextView eventTitleTV = rowView.findViewById(R.id.eventTitleTV);
        final TextView eventDetailTV = rowView.findViewById(R.id.eventDetailTV);

        EventLogger.Event current = events.get(position);
        eventTitleTV.setText(current.title);
        eventDetailTV.setText(current.detail);


        return rowView;
    }
}
