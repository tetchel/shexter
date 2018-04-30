package ca.tetchel.shexter.trust;

import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import java.util.List;

import ca.tetchel.shexter.R;
import ca.tetchel.shexter.main.MainActivity;

public class TrustedHostsListAdapter extends ArrayAdapter<String> {

    private static final String
            TAG = MainActivity.MASTER_TAG + TrustedHostsListAdapter.class.getSimpleName();

    private final TrustedHostsActivity activity;
    private final List<String> trustedHosts;

    public TrustedHostsListAdapter(TrustedHostsActivity activity, List<String> trustedHosts) {
        super(activity, R.layout.trusted_hosts_listitem, trustedHosts);
        this.activity = activity;
        this.trustedHosts = trustedHosts;
    }

    @NonNull
    @Override
    public View getView(final int position, View view, @NonNull ViewGroup parent) {
        Log.d(TAG, "getview");

        LayoutInflater inflater = activity.getLayoutInflater();
        // TODO what is this warning?
        View rowView = inflater.inflate(R.layout.trusted_hosts_listitem, null, true);
        final TextView trustedHostTV = rowView.findViewById(R.id.trustedHostTV);
        final Button trustedHostBtn = rowView.findViewById(R.id.trustedHostRemoveBtn);

        trustedHostTV.setText(trustedHosts.get(position));
        Log.d(TAG, "Set TrustedHostTV " + position + " to " + trustedHostTV.getText());

        trustedHostBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String toRemove = trustedHostTV.getText().toString();
                TrustedHostsUtilities.deleteTrustedHost(activity, toRemove);
                //Toast.makeText(activity, "Deleted " + toRemove, Toast.LENGTH_LONG);
                activity.refreshHostsList();
            }
        });

        return rowView;
    }
}
