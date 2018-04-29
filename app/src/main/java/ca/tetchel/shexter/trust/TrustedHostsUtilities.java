package ca.tetchel.shexter.trust;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import ca.tetchel.shexter.ShexterNotificationManager;
import ca.tetchel.shexter.main.MainActivity;

public class TrustedHostsUtilities {

    private static final String
            TAG = MainActivity.MASTER_TAG + TrustedHostsUtilities.class.getSimpleName();
    
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
        if(trustedHosts.contains(nameAndAddr)) {
            // shouldn't happen because the add-host stuff
            // should not be executed if it's already trusted
            Toast.makeText(context, "That host is already trusted", Toast.LENGTH_LONG).show();
            return;
        }
        trustedHosts.add(nameAndAddr);
        writeTrustedHostsList(context, trustedHosts);

        Log.d(TAG, "Added trusted host " + nameAndAddr);
        Toast.makeText(context, "Added trusted host:\n" + nameAndAddr, Toast.LENGTH_LONG)
                .show();
    }

    private static boolean isHostTrusted(Context context, String hostname, String hostAddr) {
        List<String> trustedHosts = getTrustedHostsList(context);

        String toLookFor = hostnameAddrToPrefsEntry(hostname, hostAddr);
        Log.d(TAG, "Checking if host is trusted " + toLookFor);
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
     * @param trustedHostString In the format returned by hostnameAddrToPrefsEntry
     */
    public static void deleteTrustedHost(Context context, String trustedHostString) {
        List<String> hosts = getTrustedHostsList(context);
        /*boolean deleted =*/
        hosts.remove(trustedHostString);
        Log.d(TAG, "Deleted trusted host " + trustedHostString);
        writeTrustedHostsList(context, hosts);
        //return deleted;
    }

    public static void deleteAllTrustedHosts(Context context) {
        Log.d(TAG, "CLEARING trusted hosts list");
        writeTrustedHostsList(context, new ArrayList<String>());
    }
    
    public static List<String> getTrustedHostsList(Context context) {
        Log.d(TAG, "GetTrustedHostsList");

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
        StringBuilder hostsAsPref = new StringBuilder();
        for(String host : trustedHosts) {
            hostsAsPref.append(host).append('\n');
        }
        Log.d(TAG, "Writing trusted hosts: " + hostsAsPref.toString());
        edit.putString(TRUSTED_HOSTS_PREFSKEY, hostsAsPref.toString());

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
            Log.d(TAG, "Host " + other.getCanonicalHostName() + " is NOT trusted");
            // Show user notification telling them to accept or reject the new connection
            // NB: Sometimes getCanonicalHostname returns the IP address. This is its documented behaviour, which is too
            // bad but I don't think it will happen once I stop messing with my router's DHCP
            ShexterNotificationManager.newHostNotification(context,
                    other.getHostAddress(), other.getCanonicalHostName());
        }
        else {
            Log.d(TAG, "Host " + other.getCanonicalHostName() + " is trusted already");
        }

        return valid;
    }
}
