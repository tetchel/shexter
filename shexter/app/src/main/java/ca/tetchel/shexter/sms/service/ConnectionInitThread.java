package ca.tetchel.shexter.sms.service;

import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.Charset;
import java.util.Arrays;

import static javax.xml.transform.OutputKeys.ENCODING;

class ConnectionInitThread extends Thread {
    private final String TAG = ConnectionInitThread.class.getSimpleName();

    private static final String DISCOVER_REQUEST = "shexter-discover";

    private static final int BUFFSIZE = 256;

    private DatagramSocket socket;

    ConnectionInitThread(DatagramSocket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        Log.d(TAG, "Init starting up on port " + socket.getLocalPort());

        while(SmsServerService.isRunning() && !socket.isClosed()) {
            byte[] recvBuffer = new byte[BUFFSIZE];
            DatagramPacket request = new DatagramPacket(recvBuffer, recvBuffer.length);
            try {
                Log.d(TAG, "Init ready to accept");
                socket.receive(request);

                String requestBody = new String(request.getData(), ENCODING).trim();

                if (!requestBody.equals(DISCOVER_REQUEST)) {
                    Log.i(TAG, "Received UNEXPECTED request: " + requestBody);
                }
                else {
                    Log.d(TAG, "Received discover request");
                }

                // Get the phone information. This will be displayed to the user to ID their phone.
                // Manufacturer model eg. SAMSUNG-SGH-I337
                String response = Build.MANUFACTURER.toUpperCase() + ' ' + Build.MODEL +
                        ", Android v" + Build.VERSION.RELEASE;

                Log.d(TAG, "Init thread response: " + response);

                byte[] responseBuffer = response.getBytes(Charset.forName(ENCODING));
                if (responseBuffer.length > BUFFSIZE) {
                    Log.e(TAG, "Response to be sent is too long! Will be cut off! Length is " +
                            responseBuffer.length);
                }
                else {
                    responseBuffer = Arrays.copyOf(responseBuffer, BUFFSIZE);
                }

                DatagramPacket responsePacket = new DatagramPacket(responseBuffer,
                        responseBuffer.length,
                        request.getAddress(), request.getPort());

                socket.send(responsePacket);
                Log.d(TAG, "Successfully confirmed discover.");
            } catch(UnsupportedEncodingException e) {
                Log.e(TAG, "Exception decoding from " + ENCODING, e);
            } catch (IOException e) {
                Log.e(TAG, "Exception in the InitThread", e);
            }
        }
    }
}
