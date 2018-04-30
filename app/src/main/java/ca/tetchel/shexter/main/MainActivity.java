package ca.tetchel.shexter.main;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import ca.tetchel.shexter.R;
import ca.tetchel.shexter.eventlogger.EventLogActivity;
import ca.tetchel.shexter.sms.ShexterService;
import ca.tetchel.shexter.trust.TrustedHostsActivity;

public class MainActivity extends AppCompatActivity {

    public static final String MASTER_TAG = "shexter_";
    private static final String
            TAG = MASTER_TAG + MainActivity.class.getSimpleName(),
            NEVER_DND_AGAIN_PREFSKEY = "never-ask-dnd";

    // if the user has flagged 'never ask me again' about a permission
    private boolean
            neverAskPermsAgain = false,
            needToUpdatePermissions;

    private static final int
            PERMISSION_CODE = 1234,
            SETTINGS_ACTIVITY_CODE = 1234,
            NOTIF_POLICY_ACTIVITY_CODE = 1235;

    private static final String[] requiredPermissions = {
            android.Manifest.permission.READ_CONTACTS,
            // all sms permissions seem to be lumped into one
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.SEND_SMS};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Being created.");
        requestWindowFeature(Window.FEATURE_ACTION_BAR);
        setContentView(R.layout.activity_main);

        checkAndGetPermissions();

        if (!ShexterService.isRunning()) {
            Intent shexterServiceIntent = new Intent(this, ShexterService.class);
            startService(shexterServiceIntent);
            //smsServerIntent = new Intent(this, ShexterService.class);
            bindService(shexterServiceIntent, serviceConnection, BIND_AUTO_CREATE);

            Log.d(TAG, "ShexterService has (probably) been started.");
        } else {
            Log.d(TAG, "ShexterService is already running.");
        }

    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG, "Service connecting");
            ShexterService.SmsServiceBinder binder = (ShexterService.SmsServiceBinder) iBinder;
            ShexterService boundService = binder.getService();

            Log.d(TAG, "Service bound");
            setPortTextView(boundService.getMainPortNumber());

//            boundService.setCallbacks(MainActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "Service disconnecting");
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        Intent shexterServiceIntent = new Intent(this, ShexterService.class);
        bindService(shexterServiceIntent, serviceConnection, BIND_AUTO_CREATE);
        Log.d(TAG, "OnStart");
        needToUpdatePermissions = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Resuming");
        setIPTextView();
        if (needToUpdatePermissions) {
            Log.d(TAG, "Need to update permissions");
            checkAndGetPermissions();
            needToUpdatePermissions = false;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(serviceConnection);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "OnDestroy");
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

        if (id == R.id.action_settings) {
            Toast.makeText(this, "Not implemented lol", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_refresh) {
            setIPTextView();
            Toast.makeText(this, "Updated IP", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_trustedhosts) {
            // Toast.makeText(this, "Trusted hosts woo hoo", Toast.LENGTH_SHORT).show();
            Intent trustedHostIntent = new Intent(this, TrustedHostsActivity.class);
            startActivity(trustedHostIntent);
            return true;
        }
        else if (id == R.id.action_eventlog) {
            startActivity(new Intent(this, EventLogActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setIPTextView() {
        String ip = MainUtil.getIpAddress();
        Log.d(TAG, "Updating IP Address to " + ip);
        String addressInfo = getString(R.string.your_ip_is) + " " + ip;

        ((TextView) findViewById(R.id.ipAddressTV)).setText(addressInfo);
    }

    private void setPortTextView(int portNumber) {
        // servicePort = portNumber;
        String portStr = "" + portNumber;

        Log.d(TAG, "Setting port to " + portStr);
        String portInfo = getString(R.string.port) + ' ' + portStr;

        ((TextView) findViewById(R.id.portTV)).setText(portInfo);
    }

    public void onClickPermissionsButton(View v) {
        needToUpdatePermissions = true;
        if (!neverAskPermsAgain) {
            checkAndGetPermissions();
        } else {
            // open settings page
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivityForResult(intent, SETTINGS_ACTIVITY_CODE);
        }
    }

    private void checkAndGetPermissions() {
        List<String> permissionsNeeded = new ArrayList<>(requiredPermissions.length);
        for (String s : requiredPermissions) {
            int status = ContextCompat.checkSelfPermission(this, s);
            if (status != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Require permission: " + s);
                permissionsNeeded.add(s);
            }
        }

        boolean allGood;
        if (!permissionsNeeded.isEmpty()) {
            String[] perms = permissionsNeeded.toArray(new String[0]);
            ActivityCompat.requestPermissions(this, perms, PERMISSION_CODE);
            allGood = false;
        } else {
            allGood = true;
        }

        showOrHidePermissionsRequired(!allGood);
    }

    // TODO All of this permissions stuff should be moved into a Settings activity or similar.

    /**
     *  On API 23 and up, need to request permission to change volume settings. This is required for the Ring command.
     */
    public static boolean isDndPermissionRequired() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    /**
     * On API 23 and up, need to request permission to change volume settings. This is required for the Ring command.
     * @return
     *      true if the permission is still required, meaning ring command will not work.
     *      false if permission is already given or not required.
     */
    private boolean needRequestDndPermission() {
        SharedPreferences sp = getPreferences(Context.MODE_PRIVATE);
        boolean neverAskDndAgain = sp.getBoolean(NEVER_DND_AGAIN_PREFSKEY, false);
        Log.d(TAG, "NeverAskDndAgain=" + neverAskDndAgain);

        if (!isDndPermissionRequired() || neverAskDndAgain) {
            Log.d(TAG, "Not showing DnD permission button");
            return false;
        }

        Object nmObj = getSystemService(Context.NOTIFICATION_SERVICE);
        if(nmObj == null) {
            // will this ever happen?
            String noNmMsg = "Couldn't get Notification service";
            Log.e(TAG, noNmMsg);
            Toast.makeText(this, noNmMsg, Toast.LENGTH_LONG).show();
            return true;
        }

        // ignore this error, API version is checked by isDndPermissionRequired
        boolean granted = ((NotificationManager) nmObj).isNotificationPolicyAccessGranted();
        return !granted;
    }

    public void onClickGrantDndSettings(View v) {
        // https://stackoverflow.com/questions/43123650/android-request-access-notification-policy-and-mute-phone

        // This button should never show up on API < 23
        if(isDndPermissionRequired()) {
            startActivityForResult(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
                    NOTIF_POLICY_ACTIVITY_CODE);
        }
        else {
            // if this happens, it is a bug
            Toast.makeText(this, "This permission is not required on your version of Android",
                    Toast.LENGTH_LONG).show();
        }
    }

    public void onClickPermsMoreInfo(View v) {
        AlertDialog.Builder adBuilder = new AlertDialog.Builder(this);
        adBuilder
                .setTitle(getString(R.string.more_info_dialog_title))
                .setMessage(getString(R.string.more_info_dialog_text))
                .setPositiveButton(getString(R.string.ok),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                // nada
                            }
                        })
                .setNegativeButton(getString(R.string.not_using_ring_cmd),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                onNeverAskDndAgain();
                            }
                        });
        adBuilder.show();
    }

    private void onNeverAskDndAgain() {
        AlertDialog.Builder adBuilder = new AlertDialog.Builder(this);
        adBuilder
                .setTitle(R.string.denying_dnd_perm_dialog_title)
                .setMessage(R.string.denying_dnd_perm_dialog_text)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Log.d(TAG, "User never wants to be asked about DnD permission again");

                        SharedPreferences.Editor ed = getPreferences(Context.MODE_PRIVATE).edit();
                        ed.putBoolean(NEVER_DND_AGAIN_PREFSKEY, true);
                        ed.apply();
                        checkAndGetPermissions();
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // nada
                    }
                });
        adBuilder.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        if (grantResults.length == 0) {
            showOrHidePermissionsRequired(true);
        }

        boolean allGood = true;
        for (int i = 0; i < permissions.length; i++) {
            Log.d(TAG, "Permission " + permissions[i] + " status " + grantResults[i]);

            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                allGood = false;
                Log.w(TAG, "Permission was not granted.");
                // technically, contacts is optional (could use --number)
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])) {
                    // they can NOT be asked again
                    neverAskPermsAgain = true;
                } else {
                    // they can be asked again
                    Toast.makeText(getApplicationContext(), getString(R.string.app_name) +
                                    " cannot function without Contacts and SMS permissions.",
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
        showOrHidePermissionsRequired(!allGood);
    }

    private void showOrHidePermissionsRequired(boolean showSmsContacts) {
        Log.d(TAG, "Showing that SMS/Contacts permissions are required: " + showSmsContacts);

        boolean showDnd = needRequestDndPermission();
        Log.d(TAG, "Showing that DnD permissions are required: " + showDnd);

        boolean showingAny = showSmsContacts || showDnd;
        findViewById(R.id.noPermissionsTV).setVisibility(booleanToVisiblity(showingAny));
        findViewById(R.id.permsMoreInfoBtn).setVisibility(booleanToVisiblity(showingAny));

        findViewById(R.id.permsSmsContactBtn).setVisibility(booleanToVisiblity(showSmsContacts));
        findViewById(R.id.permsDnDBtn).setVisibility(booleanToVisiblity(showDnd));

        // refresh the view by invalidating it
        (((ViewGroup) this.findViewById(android.R.id.content)).getChildAt(0)).invalidate();
    }

    private static int booleanToVisiblity(boolean b) {
        return b ? View.VISIBLE : View.GONE;
    }
}
