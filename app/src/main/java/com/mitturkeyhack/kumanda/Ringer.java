package com.mitturkeyhack.kumanda;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

/** "Telefonu çaldır" komutu: sessizde bile alarm sesiyle çalar. */
public final class Ringer {

    private static final Handler H = new Handler(Looper.getMainLooper());
    private static MediaPlayer mp;
    private static int savedVol = -1;
    private static final Runnable STOPPER = () -> stop(null);
    private static Context app;

    private Ringer() {}

    public static boolean isRinging() {
        return mp != null;
    }

    public static void start(final Context c, final int seconds) {
        H.post(() -> startMain(c.getApplicationContext(), seconds));
    }

    private static void startMain(Context c, int seconds) {
        stop(null);
        app = c;
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        try {
            savedVol = am.getStreamVolume(AudioManager.STREAM_ALARM);
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
        } catch (Exception ignored) {
        }
        Uri[] uris = {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                Settings.System.DEFAULT_ALARM_ALERT_URI,
                Settings.System.DEFAULT_NOTIFICATION_URI,
        };
        for (Uri u : uris) {
            if (u == null) continue;
            MediaPlayer p = new MediaPlayer();
            try {
                p.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                p.setDataSource(c, u);
                p.setLooping(true);
                p.prepare();
                p.start();
                mp = p;
                break;
            } catch (Exception e) {
                try {
                    p.release();
                } catch (Exception ignored) {
                }
            }
        }
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(VibrationEffect.createWaveform(new long[]{0, 800, 600}, 0));
        } catch (Exception ignored) {
        }
        H.postDelayed(STOPPER, Math.max(5, seconds) * 1000L);

        Intent i = new Intent(c, RingActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        BlockService.launch(c, i);
    }

    /** Herhangi bir iş parçacığından çağrılabilir. */
    public static void stop(Context unused) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            H.post(() -> stop(null));
            return;
        }
        H.removeCallbacks(STOPPER);
        if (mp != null) {
            try {
                mp.stop();
            } catch (Exception ignored) {
            }
            try {
                mp.release();
            } catch (Exception ignored) {
            }
            mp = null;
        }
        if (app != null) {
            try {
                Vibrator v = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) v.cancel();
            } catch (Exception ignored) {
            }
            if (savedVol >= 0) {
                try {
                    AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
                    am.setStreamVolume(AudioManager.STREAM_ALARM, savedVol, 0);
                } catch (Exception ignored) {
                }
                savedVol = -1;
            }
        }
        RingActivity.closeAll();
    }
}
