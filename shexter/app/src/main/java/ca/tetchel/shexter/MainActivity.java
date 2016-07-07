package ca.tetchel.shexter;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class MainActivity extends Activity {

    private final String TAG = "Shexter_Main";

    private static final int[] permissionCodes = { 1234, 1235, 1236, 1237, 1238 };

    //order must match the order of permissionCodes
    private static final String[] requiredPermissions = {
            android.Manifest.permission.INTERNET,
            android.Manifest.permission.READ_CONTACTS,
//            android.Manifest.permission.READ_SMS,
//            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.SEND_SMS };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView ipAddrTV = (TextView) findViewById(R.id.ipAddressTV);
        ipAddrTV.setText(getIpAddress());

        checkAndGetPermissions();

        Intent serverIntent = new Intent(this, SmsServerService.class);

        if(!isServiceRunning(SmsServerService.class)) {
            startService(serverIntent);
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

        return super.onOptionsItemSelected(item);
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        for (ActivityManager.RunningServiceInfo service : manager
                .getRunningServices(Integer.MAX_VALUE)) {

            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private String getIpAddress() {
        String ip = "";
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
                        ip += "Your subnet IP Address is: "
                                + inetAddress.getHostAddress() + "\n";
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG, "Error getting IP address", e);
            ip += "Error: " + e.toString() + "\n";
        }
        return ip;
    }

    private void checkAndGetPermissions() {
        for(int i = 0; i < requiredPermissions.length; i++) {
            String p = requiredPermissions[i];
            if(ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, p)) {
                    // Show an expanation to the user *asynchronously* -- don't block
                    // this thread waiting for the user's response! After the user
                    // sees the explanation, try again to request the permission.
                    ActivityCompat.requestPermissions(this,
                            new String[]{p}, permissionCodes[i]);
                } else {
                    ActivityCompat.requestPermissions(this,
                            new String[]{p}, permissionCodes[i]);
                }
            }
            else {
                Log.d(TAG, "Permission was already granted: " + requiredPermissions[i]);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        int requestCodeIndex = -1;
        for(int i = 0; i < permissionCodes.length; i++) {
            if(requestCode == permissionCodes[i]) {
                requestCodeIndex = i;
            }
        }
        // If request is cancelled, the result arrays are empty.
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permission was granted: " + requiredPermissions[requestCodeIndex]);
        }
        else {
            Log.w(TAG, "Permission was not granted: " + requiredPermissions[requestCodeIndex]);
        }
    }
}
