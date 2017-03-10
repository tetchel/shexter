package ca.tetchel.shexter.sms.service;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;

import ca.tetchel.shexter.R;
import ca.tetchel.shexter.SmsReceiver;

import static ca.tetchel.shexter.sms.ServiceConstants.PORT_MAX;
import static ca.tetchel.shexter.sms.ServiceConstants.PORT_MIN;

public class SmsServerService extends Service {

    private static final String TAG = SmsServerService.class.getSimpleName();

    // Singleton instance to be called to get access to the Application Context from static code
    private static SmsServerService INSTANCE;

    // Fields are static - this is a singleton

    // TCP initSocket for receiving and processing app requests
    private static ServerSocket serverSocket;
    // UDP initSocket for receiving DISCOVER and picking a port for the above initSocket
    private static DatagramSocket initSocket;

    private static SmsServerThread serverThread;
    private static ConnectionInitThread initThread;

    // Register to receive new SMS intents
    private static SmsReceiver receiver;

    // Probably should remove this
//    WifiManager.WifiLock wifiLock;

    /**
     * Access the singleton instance of this class for getting the context.
     *
     * @return The singleton instance.
     */
    public static SmsServerService instance() {
        return INSTANCE;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Enter onBind");
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG, "Enter onStartCommand");
        INSTANCE = this;

        int[] port = new int[1];
        port[0] = PORT_MIN;
        boolean success = openSocket(true, port);
        int initPort = port[0];
        port[0]++;
        success = success && openSocket(false, port);
        if(!success) {
            // TODO handle this better :D
            Toast.makeText(getApplicationContext(), "Everything is doomed", Toast.LENGTH_LONG)
                    .show();
            Log.e(TAG, "Everything is doomed. Init is port " + initPort + " and main socket is "
                    + port[0]);
        }
        else {
            Log.d(TAG, "Successful socket creations. initPort: " + initPort + " Other port: " +
                    port[0]);
        }

        // TODO move this logic to the Thread classes?
        serverThread = new SmsServerThread(serverSocket);
        initThread = new ConnectionInitThread(initSocket);

        serverThread.start();
        initThread.start();
        receiver = new SmsReceiver();

        /*
        WifiManager wifiManager = (WifiManager) getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);

        if(wifiManager != null) {
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL,
                    TAG);
            wifiLock.setReferenceCounted(true);
            wifiLock.acquire();
            Log.d(TAG, "Locked WIFI");
        }
        */

        Log.d(TAG, getString(R.string.app_name) + " service started.");

        return START_STICKY;
    }

    private boolean openSocket(boolean isInitSocket, int[] port) {
        boolean success = false;

        while(!success && port[0] <= PORT_MAX) {
            try {
                if(isInitSocket) {
                    initSocket = new DatagramSocket(port[0]);
                    initSocket.setBroadcast(true);
                }
                else {
                    serverSocket = new ServerSocket(port[0]);
                }
                success = true;
            } catch (IOException e) {
                Log.w(TAG, "Exception opening socket: isInit: " + isInitSocket + " Port is " +
                                port[0], e);
                port[0]++;
            }
        }
        return success;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Enter onDestroy");

        /*
        if(wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
        */

        serverThread.interrupt();
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                Log.d(TAG, "Closed ServerSocket");
            }
            else {
                Log.w(TAG, "ServerSocket was not open!");
            }
        } catch (IOException e) {
            Log.e(TAG, "Exception closing ServerSocket", e);
        }

        initThread.interrupt();
        if (initSocket != null && !initSocket.isClosed()) {
            initSocket.close();
            Log.d(TAG, "Closed InitSocket");
        }
        else {
            Log.w(TAG, "InitSocket was not open!");
        }

        Log.d(TAG, getString(R.string.app_name) + " service stopped.");
    }

    /**
     * @return If this service is running. Only one instance of this service can run at a time.
     */
    static boolean isRunning() {
        SmsServerService instance = instance();
        if(instance == null) {
            return false;
        }

        ActivityManager manager = (ActivityManager) instance.getApplicationContext()
                .getSystemService(Context.ACTIVITY_SERVICE);

        for (ActivityManager.RunningServiceInfo runningService : manager
                .getRunningServices(Integer.MAX_VALUE)) {

            if (SmsServerService.class.getName().equals(runningService.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static SmsReceiver getSmsReceiver() {
        return receiver;
    }
}