package ca.tetchel.shexter.sms;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicInteger;

import ca.tetchel.shexter.R;
import ca.tetchel.shexter.receiver.SmsReceiver;
import ca.tetchel.shexter.sms.subservices.ConnectionInitThread;
import ca.tetchel.shexter.sms.subservices.SmsServerThread;
import ca.tetchel.shexter.sms.util.ServiceConstants;

public class ShexterService extends Service {

    private static final String TAG = ShexterService.class.getSimpleName();

    // Binder used to communicate with bound activities
    private final IBinder binder = new SmsServiceBinder();
    // Callback methods implemented by bound activities
//    private SmsServiceCallbacks smsServiceCallbacks;

    // Singleton instance to be called to get access to the Application Context from static code
    private static ShexterService INSTANCE;

    // TCP initSocket for receiving and processing app requests
    private ServerSocket serverSocket;
    // UDP initSocket for receiving DISCOVER and picking a port for the above initSocket
    private DatagramSocket initSocket;

    private SmsServerThread serverThread;
    private ConnectionInitThread initThread;

    // Register to receive new SMS intents
    private SmsReceiver receiver;

    // Probably should remove this
//    WifiManager.WifiLock wifiLock;

    /**
     * Access the singleton instance of this class for getting the context.
     */
    public static ShexterService instance() {
        return INSTANCE;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG, "Enter onStartCommand");
        INSTANCE = this;

        // We have to open two ports. One for the connection init service, and one for the
        // main sms server service. The ports used then have to be passed to the MainActivity to
        // be displayed to the user.
        AtomicInteger port = new AtomicInteger(ServiceConstants.PORT_MIN);
        boolean success = openSocket(true, port);
        int initPort = port.get();
        if(!success) {
            Log.e(TAG, "Failed to open init service socket! Port is " + initPort);
            // we can still try to open the main socket, I guess.
        }

        port.addAndGet(1);
        success = openSocket(false, port);
        if(!success) {
            // TODO handle this better
            Toast.makeText(getApplicationContext(),
                    "Failed to open service socket!", Toast.LENGTH_LONG)
                    .show();

            Log.e(TAG, "Everything is doomed. Init is port " + initPort + " and main socket is "
                    + port.get());

            return START_STICKY;
        }
        else {
            Log.d(TAG, "Successful socket creations. initPort: " + initPort + " Other port: " +
                    port.get());
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

    private boolean openSocket(boolean isInitSocket, AtomicInteger port) {
        boolean success = false;

        while(!success && port.get() <= ServiceConstants.PORT_MAX) {
            try {
                if(isInitSocket) {
                    initSocket = new DatagramSocket(port.get());
                    initSocket.setBroadcast(true);
                }
                else {
                    serverSocket = new ServerSocket(port.get());
                }
                success = true;
            } catch (IOException e) {
                Log.w(TAG, "Exception opening socket: isInit: " + isInitSocket + " Port is " +
                                port.get(), e);
                port.addAndGet(1);
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
    public static boolean isRunning() {
        ShexterService instance = instance();
        if(instance == null) {
            return false;
        }

        ActivityManager manager = (ActivityManager) instance.getApplicationContext()
                .getSystemService(Context.ACTIVITY_SERVICE);

        if(manager == null) {
            Log.e(TAG, "Couldn't get ActivityManager!");
            return false;
        }

        for (ActivityManager.RunningServiceInfo runningService : manager
                .getRunningServices(Integer.MAX_VALUE)) {

            if (ShexterService.class.getName().equals(runningService.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public int getMainPortNumber() {
        if(serverSocket == null) {
            return -1;
        }
        return serverSocket.getLocalPort();
    }

    public SmsReceiver getSmsReceiver() {
        return receiver;
    }

    ///// Code that allows binding to this service /////

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Enter onBind");
        return binder;
    }

//    public interface SmsServiceCallbacks {
//    }

    //    public void setCallbacks(SmsServiceCallbacks callbacks) {
//        smsServiceCallbacks = callbacks;
//    }

    public class SmsServiceBinder extends Binder {
        public ShexterService getService() {
            return ShexterService.this;
        }
    }
}