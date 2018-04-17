package ca.tetchel.shexter.trust;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import ca.tetchel.shexter.ShexterNotificationManager;

public class TrustedHostsUtilities {

    private static final String TAG = TrustedHostsUtilities.class.getSimpleName();
    
    private static final String TRUSTED_HOSTS_PREFSKEY = "trusted_hosts";

    /**
     * Store the given host information as a trusted host,
     * accepting that a (hostname, ipaddr) pairing is enough to uniquely identify a client.
     *
     * In theory, an attacker could change their client host's settings to match these,
     * and bypass the security check in this way. Perhaps it could be improved by having the client
     * send a unique identifier such as MAC address, but this would require protocol changes.
     */
    public static void addKnownHost(Context context, String hostAddr, String hostname) {
        String nameAndAddr = hostnameAddrToPrefsEntry(hostname, hostAddr);

        List<String> trustedHosts = getTrustedHostsList(context);
        trustedHosts.add(nameAndAddr);
        writeTrustedHostsList(context, trustedHosts);

        Toast.makeText(context, "Added trusted host:\n" + nameAndAddr, Toast.LENGTH_LONG)
                .show();
    }

    private static boolean isHostTrusted(Context context, String hostname, String hostAddr) {
        List<String> trustedHosts = getTrustedHostsList(context);

        String toLookFor = hostnameAddrToPrefsEntry(hostname, hostAddr);
        for(String host : trustedHosts) {
            if (host.equals(toLookFor)) {
                return true;
            }
        }
        return false;
    }
    
    private static String hostnameAddrToPrefsEntry(String hostname, String hostAddr) {
        return hostname + " @ " + hostAddr;
    }

    /**
     * @param trustedHostString In the format retunred by hostnameAddrToPrefsEntry
     */
    public static void deleteTrustedHost(Context context, String trustedHostString) {
        List<String> hosts = getTrustedHostsList(context);
        /*boolean deleted =*/
        hosts.remove(trustedHostString);
        writeTrustedHostsList(context, hosts);
        //return deleted;
    }

    public static void deleteAllTrustedHosts(Context context) {
        writeTrustedHostsList(context, new ArrayList<String>());
    }
    
    public static List<String> getTrustedHostsList(Context context) {
        SharedPreferences sp = context.getSharedPreferences(TRUSTED_HOSTS_PREFSKEY, Context.MODE_PRIVATE);
        String currentTrusted = sp.getString(TRUSTED_HOSTS_PREFSKEY, "");

        List<String> trustedHosts = new ArrayList<>();
        for(String host : currentTrusted.split("\n")) {
            String hostTrimmed = host.trim();
            if(!hostTrimmed.isEmpty()) {
                trustedHosts.add(hostTrimmed);
            }
        }

        return trustedHosts;
    }

    private static void writeTrustedHostsList(Context context, List<String> trustedHosts) {
        SharedPreferences sp = context.getSharedPreferences(TRUSTED_HOSTS_PREFSKEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = sp.edit();
        StringBuilder prefs = new StringBuilder();
        for(String host : trustedHosts) {
            prefs.append(host).append('\n');
        }
        Log.d(TAG, "Writing trusted hosts: " + prefs.toString());
        edit.putString(TRUSTED_HOSTS_PREFSKEY, prefs.toString());

        edit.apply();
    }

    /**
     * Return if the other host belongs to the list of hosts approved to connect to the phone.
     */
    public static boolean isHostTrusted(Context context, InetAddress other) {
        Log.d(TAG, "Validating request from " + other + " - hostname " +
                other.getCanonicalHostName());

        boolean valid = isHostTrusted(context, other.getCanonicalHostName(), other.getHostAddress());

        if(!valid) {
            // Show user notification telling them to accept or reject the new connection
            ShexterNotificationManager.newHostNotification(context,
                    other.getHostAddress(), other.getCanonicalHostName());
        }

        return valid;
    }
}
