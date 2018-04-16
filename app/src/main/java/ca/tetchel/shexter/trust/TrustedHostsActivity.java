package ca.tetchel.shexter.trust;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.Window;
import android.widget.ListView;

import ca.tetchel.shexter.R;
import ca.tetchel.shexter.ShexterNotificationManager;

public class TrustedHostsActivity extends AppCompatActivity {

    private static final String TAG = TrustedHostsActivity.class.getSimpleName();

    public static final String
            HOST_ADDR_INTENTKEY = "hostAddr",
            HOSTNAME_INTENTKEY = "hostname";

    private AlertDialog newHostDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "OnCreate");
        requestWindowFeature(Window.FEATURE_ACTION_BAR);
        setContentView(R.layout.activity_trusted_hosts);

        String hostAddr = getIntent().getStringExtra(HOST_ADDR_INTENTKEY);
        String hostname = getIntent().getStringExtra(HOSTNAME_INTENTKEY);
        if (!(hostAddr == null && hostname == null)) {
            Log.d(TAG, "Host Addr: " + hostAddr + ", Hostname: " + hostname);
            onNewHost(hostAddr, hostname);
        } else {
            Log.d(TAG, "Launched without hostname extras");
        }

        refreshHostsList();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Destroy");
        if(newHostDialog != null) {
            newHostDialog.dismiss();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_trustedhosts, menu);
        return true;
    }

    public void refreshHostsList() {
        Log.d(TAG, "Refresh hosts list");
        final ListView hostsList = findViewById(R.id.trustedHostsLV);

        TrustedHostsListAdapter adapter = new TrustedHostsListAdapter(this,
                TrustedHostsUtilities.getTrustedHostsList(this));

        hostsList.setAdapter(adapter);
    }

    private void onNewHost(final String hostAddr, final String hostname) {
        if(hostAddr == null || hostname == null) {
            Log.e(TAG, "Only one of hostaddr or hostname was null, which should not happen: "+
                    "HostAddr: " + hostAddr + ", Hostname: " + hostname);
            return;
        }

        String msg = "Request to connect from " + hostname + " at " + hostAddr + '\n' +
                "If you did not just try to connect to " + getString(R.string.app_name) +
                " from your computer, REJECT it!";

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder .setTitle("Incoming connection")
                .setMessage(msg)
                .setPositiveButton("Accept", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        TrustedHostsUtilities.addKnownHost(TrustedHostsActivity.this,
                                hostAddr, hostname);
                        onAcceptOrRejectHost(true);
                    }
                })
                .setNegativeButton("Reject", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onAcceptOrRejectHost(false);
                    }
                });

        newHostDialog = builder.show();
    }

    private void onAcceptOrRejectHost(boolean accepted) {
        Log.d(TAG, "User " + (accepted ? "accepted" : "rejected") + " incoming connection");
        ShexterNotificationManager.clearNewHostNotif(this);
        finish();
    }


}
