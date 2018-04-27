package ca.tetchel.shexter.main;

import android.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

// Static utilities to support the MainActivity
class MainUtil {

    private static final String TAG = MainActivity.MASTER_TAG + MainUtil.class.getSimpleName();

    /**
     * Get the LAN IP address of this device.
     * Whatever this returns will be displayed directly to the user.
     */
    public static String getIpAddress() {
        Enumeration<NetworkInterface> ifaces;
        try {
            ifaces = NetworkInterface.getNetworkInterfaces();
        }
        catch (SocketException e) {
            Log.e(TAG, "Error getting IP address", e);
            return "Error: " + e.toString();
        }

        Log.d(TAG, "Got network interfaces:");

        String addrResult = null;
        boolean foundIpv6 = false;
        while (ifaces.hasMoreElements()) {
            NetworkInterface iface = ifaces.nextElement();
            Log.d(TAG, "Found NetworkInterface " + iface.getDisplayName());

            Enumeration<InetAddress> ifaceAddresses = iface.getInetAddresses();
            while (ifaceAddresses.hasMoreElements()) {
                InetAddress address = ifaceAddresses.nextElement();
                Log.d(TAG, "Found InetAddress: " + address);

                if (address.isSiteLocalAddress() && !address.isLoopbackAddress()) {
                    String hostAddr = address.getHostAddress();
                    
                    // just take the first ipv4 address, I guess?
                    if(addrResult == null && address instanceof Inet4Address) {
                        Log.d(TAG, "Saving address: " + hostAddr);

                        // Could return here, but I like logging all the possible addresses
                        addrResult = hostAddr;
                    }
                    else {
                        Log.d(TAG, "Rejected IPv6 address: " + hostAddr);
                        // I have no way to handle ipv6-only device at this time
                        foundIpv6 = true;
                    }
                }
            }
        }

        if(addrResult != null) {
            // it worked
            return addrResult;
        }
        // it did not work
        else if (foundIpv6) {
            return "Could not get an IPv4 address!";
        }
        else {
            return "Could not get any IP address!";
        }
    }
}
