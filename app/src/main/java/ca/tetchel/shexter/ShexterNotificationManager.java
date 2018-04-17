package ca.tetchel.shexter;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import ca.tetchel.shexter.trust.TrustedHostsActivity;

public class ShexterNotificationManager {

    private static final String TAG = ShexterNotificationManager.class.getSimpleName();

    private static final int NEW_HOST_NOTIF_ID = 1234;
    private static final String CHANNEL_ID = "shexter";

    public static void newHostNotification(Context context, String hostAddr, String hostname) {
        NotificationCompat.Builder notifBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(),
                        R.mipmap.ic_launcher))
                .setContentTitle(context.getString(R.string.new_connection_request))
                .setContentText(context.getString(R.string.incoming_request_from_hostname, hostname))
                .setDefaults(Notification.DEFAULT_ALL);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notifBuilder.setPriority(NotificationManager.IMPORTANCE_MAX);
        }
        else {
            notifBuilder.setPriority(Notification.PRIORITY_MAX);
        }

        // On clicking this notification, open the TrustedHostsActivity with the new host
        // info as extras
        Intent approvalIntent = new Intent(context, TrustedHostsActivity.class);
        approvalIntent.putExtra(TrustedHostsActivity.HOST_ADDR_INTENTKEY, hostAddr);
        approvalIntent.putExtra(TrustedHostsActivity.HOSTNAME_INTENTKEY, hostname);
        PendingIntent notifIntent = PendingIntent.getActivity(context,
                0, approvalIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        notifBuilder.setContentIntent(notifIntent);

        //builder.setOngoing(true);
        NotificationManager nm = tryGetNotifManager(context);
        if(nm != null) {
            nm.notify(NEW_HOST_NOTIF_ID, notifBuilder.build());
        }
    }

    public static void clearNewHostNotif(Context context) {
        NotificationManager nm = tryGetNotifManager(context);
        if(nm != null) {
            nm.cancel(NEW_HOST_NOTIF_ID);
        }
    }

    @Nullable
    private static NotificationManager tryGetNotifManager(Context context) {
        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        if(notificationManager == null) {
            Log.e(TAG, "Unable to get notification manager!!");
        }
        return notificationManager;
    }
}
