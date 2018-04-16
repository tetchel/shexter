package ca.tetchel.shexter;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class TrustedHostsListAdapter extends ArrayAdapter<String> {

    private static final String TAG = TrustedHostsListAdapter.class.getSimpleName();

    private final TrustedHostsActivity activity;
    private final List<String> trustedHosts;

    public TrustedHostsListAdapter(TrustedHostsActivity activity_, List<String> trustedHosts_) {
        super(activity_, R.layout.trusted_hosts_listitem, trustedHosts_);
        activity = activity_;
        trustedHosts = trustedHosts_;
    }

    @NonNull
    @Override
    public View getView(final int position, View view, @NonNull ViewGroup parent) {
        Log.d(TAG, "getview");

        LayoutInflater inflater = activity.getLayoutInflater();
        // TODO what is this warning?
        View rowView = inflater.inflate(R.layout.trusted_hosts_listitem, null, true);
        final TextView trustedHostTV = rowView.findViewById(R.id.trustedHostTV);
        Button trustedHostBtn = rowView.findViewById(R.id.trustedHostRemoveBtn);

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
