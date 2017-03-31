package ca.tetchel.shexter.sms.service;

import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.Charset;
import java.util.Arrays;

class ConnectionInitThread extends Thread {
    private final String TAG = ConnectionInitThread.class.getSimpleName();

    private static final String DISCOVER_REQUEST = "shexter-discover",
                                ENCODING = "utf-8";

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

                if (!requestBody.startsWith(DISCOVER_REQUEST)) {
                    Log.i(TAG, "Received UNEXPECTED request: " + requestBody);
                }
                else {
                    Log.d(TAG, "Received discover request. Body: " + requestBody);
                }

                // Get the phone information. This will be displayed to the user to ID their phone.
                // Manufacturer model eg. SAMSUNG-SGH-I337
                String response = "shexter-confirm\n" + Build.MANUFACTURER.toUpperCase() + ' ' +
                        Build.MODEL + ", Android v" + Build.VERSION.RELEASE;

                response += '\n' + "Port: " + SmsServerService.instance().getMainPortNumber();

                Log.d(TAG, "Init thread response: " + response);

                byte[] responseBuffer = response.getBytes(Charset.forName(ENCODING));
                if (responseBuffer.length > BUFFSIZE) {
                    Log.e(TAG, "Response to be sent is too long! Will be cut off! Length is " +
                            responseBuffer.length);
                }
                else {
                    responseBuffer = Arrays.copyOf(responseBuffer, BUFFSIZE);
                }

                // The broadcast appears to arrive on a different port than it gets sent on
                // The correct port is provided in the request on the second line
                int requestPortIndex = requestBody.indexOf('\n') + 1;
                if(requestPortIndex == -1) {
                    Log.d(TAG, "Request didn't have a new line: index is " + requestPortIndex);
                    Log.d(TAG, "Malformed request: " + requestBody);
                    return;
                }

                try {
                    String requestPort = requestBody.substring(requestPortIndex);
                    int requestPortInt = Integer.parseInt(requestPort);
                    Log.d(TAG, "Responded to port " + requestPortInt);

                    DatagramPacket responsePacket = new DatagramPacket(responseBuffer,
                            responseBuffer.length,
                            request.getAddress(), requestPortInt);
                    socket.send(responsePacket);

                    Log.d(TAG, "Successfully confirmed discover.");
                } catch(NumberFormatException e) {
                    Log.e(TAG, "Malformed request; second line is not a number: " + requestBody, e);
                }
            } catch(UnsupportedEncodingException e) {
                Log.e(TAG, "Exception decoding from " + ENCODING, e);
            } catch (IOException e) {
                Log.e(TAG, "Exception in the InitThread", e);
            }
        }
    }
}
