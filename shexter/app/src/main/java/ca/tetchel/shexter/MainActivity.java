package ca.tetchel.shexter;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import ca.tetchel.shexter.sms.service.SmsServerService;

public class MainActivity extends Activity implements SmsServerService.SmsServiceCallbacks {

    private final String TAG = "Main";

    // if the user has flagged 'never ask me again' about a permission
    private boolean neverAgain = false,
                    needToUpdatePermissions;

    private SmsServerService boundService;

    // each permission has to have a unique 'code' to ID whether user accepted it or not
    private static final int    PERMISSION_CODE = 1234,
            SETTINGS_ACTIVITY_CODE = 1234;

    // order must match the order of permissionCodes
    private static final String[] requiredPermissions = {
            android.Manifest.permission.INTERNET,
            android.Manifest.permission.READ_CONTACTS,
            // all sms permissions seem to be lumped into one
//            android.Manifest.permission.READ_SMS,
//            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.SEND_SMS };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Being created.");
        setContentView(R.layout.activity_main);

        checkAndGetPermissions();

        if(!SmsServerService.isRunning()) {
            Intent smsServerIntent = new Intent(this, SmsServerService.class);
            startService(smsServerIntent);
            bindService(smsServerIntent, serviceConnection, BIND_AUTO_CREATE);
            Log.d(TAG, "SmsServerService has (probably) been started.");
        } else {
            Log.d(TAG, "SmsServerService is already running.");
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG, "Service connecting");
            SmsServerService.SmsServiceBinder binder = (SmsServerService.SmsServiceBinder) iBinder;
            boundService = binder.getService();
            boundService.setCallbacks(MainActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "Service disconnecting");
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "Starting up.");
        needToUpdatePermissions = true;
        setIPTextView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Resuming");
        if(needToUpdatePermissions) {
            Log.d(TAG, "Need to update permissions");
            checkAndGetPermissions();
            needToUpdatePermissions = false;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
//        else if(id == R.id.action_refresh) {
//            updateIpAddress();
//            Toast.makeText(this.getApplicationContext(), "Updated IP address.", Toast.LENGTH_SHORT)
//                    .show();
//            return true;
//        }

        return super.onOptionsItemSelected(item);
    }

    private void setIPTextView() {
        String ip = getIpAddress();
        Log.d(TAG, "Updating IP Address to " + ip);
        String addressInfo = getString(R.string.your_ip_is) + '\n' + ip;

        ((TextView) findViewById(R.id.ipAddressTV)).setText(addressInfo);
    }

    private void setPortTextView(int port) {
        String portStr = "" + port;
        if (port == -1) {
            portStr = "Error!";
        }

        Log.d(TAG, "Setting port to " + portStr);
        String portInfo = getString(R.string.port) + '\n' + portStr;

        ((TextView) findViewById(R.id.portTV)).setText(portInfo);
    }

    private String getIpAddress() {
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface
                    .getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces
                        .nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface
                        .getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();

                    if (inetAddress.isSiteLocalAddress()) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        }
        catch (SocketException e) {
            Log.e(TAG, "Error getting IP address", e);
            return "Error: " + e.toString() + "\n";
        }
        return "Not found!";
    }

    public void onClickPermissionsButton(View v) {
        needToUpdatePermissions = true;
        if(!neverAgain) {
            checkAndGetPermissions();
        }
        else {
            // open settings page
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivityForResult(intent, SETTINGS_ACTIVITY_CODE);
        }
    }

    private void checkAndGetPermissions() {
        List<String> permissionsNeeded = new ArrayList<>(requiredPermissions.length);
        for(String s : requiredPermissions) {
            int status = ContextCompat.checkSelfPermission(this, s);
            if(status != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Require permission: " + s);
                permissionsNeeded.add(s);
            }
        }

        boolean allGood;
        if (!permissionsNeeded.isEmpty()) {
            String[] perms = permissionsNeeded.toArray(new String[0]);
            ActivityCompat.requestPermissions(this, perms, PERMISSION_CODE);
            allGood = false;
        }
        else {
            allGood = true;
        }
        showOrHidePermissionsRequired(!allGood);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        if(grantResults.length == 0) {
            showOrHidePermissionsRequired(true);
        }

        boolean allGood = true;
        for(int i = 0; i < permissions.length; i++) {
            Log.d(TAG, "Permission " + permissions[i] + " status " + grantResults[i]);

            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                allGood = false;
                Log.w(TAG, "Permission was not granted.");
                // technically, contacts is optional (could use --number)
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])) {
                    // they can NOT be asked again
                    neverAgain = true;
                }
                else {
                    // they can be asked again
                    // technically contacts permission is optional if they use -n flag always
                    Toast.makeText(getApplicationContext(), getString(R.string.app_name) +
                            " cannot function without Contacts and SMS permissions.",
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
        showOrHidePermissionsRequired(!allGood);
    }

    private void showOrHidePermissionsRequired(boolean show) {
        Log.d(TAG, "Showing that showing permissions are required: " + show);
        int visibility = show ? View.VISIBLE : View.GONE;
        findViewById(R.id.noPermissionsTV).setVisibility(visibility);
        findViewById(R.id.permissionsButton).setVisibility(visibility);

        // refresh the view by invalidating it
        (((ViewGroup) this.findViewById(android.R.id.content)).getChildAt(0)).invalidate();
    }

    @Override
    public void onServerRunning(int portNumber) {
        Log.d(TAG, "OnServerRunning");

        setPortTextView(portNumber);
    }
}
