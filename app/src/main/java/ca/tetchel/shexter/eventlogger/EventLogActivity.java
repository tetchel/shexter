package ca.tetchel.shexter.eventlogger;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.ListView;

import ca.tetchel.shexter.R;
import ca.tetchel.shexter.main.MainActivity;

public class EventLogActivity extends AppCompatActivity {

    private static final String
            TAG = MainActivity.MASTER_TAG + EventLogActivity.class.getSimpleName();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "OnCreate");
        requestWindowFeature(Window.FEATURE_ACTION_BAR);
        setContentView(R.layout.activity_trusted_hosts);

        refreshEventList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Resume");
        refreshEventList();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Destroy");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_trustedhosts, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        return super.onOptionsItemSelected(item);
    }

    public void refreshEventList() {
        Log.d(TAG, "Refresh event list");
        final ListView eventsList = findViewById(R.id.trustedHostsLV);
        EventLogListAdapter adapter = new EventLogListAdapter(this, EventLogger.getEvents());
        eventsList.setAdapter(adapter);
    }

}