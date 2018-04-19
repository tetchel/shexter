package ca.tetchel.shexter;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.Ringtone;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

public class RingCommandActivity extends AppCompatActivity {

    private static final String TAG = RingCommandActivity.class.getSimpleName();

    public static final String STOP_RINGING_INTENTKEY = "stop_ringing";

    // These are set before this activity is created by the CommandProcessor
    // ugly way to pass it but better than using Parcelable
    public static Ringtone ringtone;
    public static Vibrator vibrator;

    private static final int RING_STREAM = AudioManager.STREAM_RING;
    // These are set by startPlaying right before maxing out the volume, and restored by stopPlaying
    private int originalMode = -1, originalVolume = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Being created.");
        setContentView(R.layout.activity_ringing);

        startPlaying();
    }

    private void startPlaying() {
        if(ringtone == null) {
            Log.e(TAG, "Requested start playing, but ringtone is null");
            return;
        }
        else if(ringtone.isPlaying()) {
            Log.i(TAG, "Requested start playing, but already playing");
            return;
        }

        // jack up the ringtone volume
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if(audioManager != null) {
            originalMode = audioManager.getRingerMode();
            originalVolume = audioManager.getStreamVolume(RING_STREAM);
            audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            audioManager.setStreamVolume(RING_STREAM, audioManager.getStreamMaxVolume(RING_STREAM), 0);
        }
        else {
            Log.e(TAG, "Couldn't get AudioManager!");
        }

        ringtone.play();
        vibrate();
        ShexterNotificationManager.ringNotification(this);
    }

    private void stopPlaying() {
        if(ringtone == null) {
            Log.e(TAG, "Requested stop playing, but ringtone is null!");
            return;
        }

        // reset the ringtone volume
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if(audioManager != null) {
            if(originalMode != -1) {
                audioManager.setRingerMode(originalMode);
            }
            if(originalVolume != -1) {
                audioManager.setStreamVolume(RING_STREAM, originalVolume, 0);
            }
        }
        else {
            Log.e(TAG, "Couldn't get AudioManager 2!");
        }

        ringtone.stop();
        stopVibrate();
        ShexterNotificationManager.clearRingNotif(this);
        // Toast.makeText(this, getString(R.string.ringing_stopped), Toast.LENGTH_SHORT).show();
    }

    private void vibrate() {
        if(vibrator == null) { return; }

        long[] pattern = new long[] { 1000, 1000 };
        int repeat = 0;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat));
            //vibrator.vibrate(VibrationEffect.createOneShot(1000, 255));
        }
        else {
            vibrator.vibrate(pattern, repeat);
            //vibrator.vibrate(1000);
        }
    }

    private void stopVibrate() {
        if(vibrator == null) { return; }
        vibrator.cancel();
    }

    public void onClickMute(View v) {
        stopPlaying();
        finish();
    }

    @Override
    protected void onNewIntent(Intent newIntent) {
        // To be run when the user clicks the Ringing notification
        if(newIntent.getBooleanExtra(STOP_RINGING_INTENTKEY, false)) {
            stopPlaying();
            finish();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // stopPlaying();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPlaying();
        Log.d(TAG, "OnDestroy");
    }
}
