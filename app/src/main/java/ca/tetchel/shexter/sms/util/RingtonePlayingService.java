package ca.tetchel.shexter.sms.util;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

public class RingtonePlayingService extends Service {

    private static final String TAG = RingtonePlayingService.class.getSimpleName();

    public static final String RINGTONE_URI_INTENTKEY = "ringtone-uri";

    private Ringtone ringtone;
    private Timer ringtoneRefresher;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.d(TAG, "Started");
        if(intent.hasExtra(RINGTONE_URI_INTENTKEY)) {
            Uri ringtoneUri = Uri.parse(intent.getExtras().getString(RINGTONE_URI_INTENTKEY, ""));
            Log.d(TAG, "Has the extra, URI is " + ringtoneUri);
            ringtone = RingtoneManager.getRingtone(this, ringtoneUri);
            ringtone.play();

            ringtoneRefresher = new Timer();
            ringtoneRefresher.scheduleAtFixedRate(new TimerTask() {
                public void run() {
                    if (!ringtone.isPlaying()) {
                        ringtone.play();
                    }
                }
            }, 1000, 1000);
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        ringtone.stop();
        ringtoneRefresher.cancel();
    }

    public static void destroyAll(Context context) {
        ActivityManager manager = (ActivityManager)context.getApplicationContext()
                .getSystemService(Context.ACTIVITY_SERVICE);

        if(manager == null) {
            Log.e(TAG, "Couldn't get ActivityManager!");
            return;
        }

        for (ActivityManager.RunningServiceInfo runningService : manager
                .getRunningServices(Integer.MAX_VALUE)) {

            if (RingtonePlayingService.class.getName()
                    .equals(runningService.service.getClassName())) {
                Intent stopServiceIntent = new Intent(context, runningService.service.getClass());
                context.stopService(stopServiceIntent);
                Log.d(TAG, "Destroyed a playing ringtone");
            }
        }
    }
}
