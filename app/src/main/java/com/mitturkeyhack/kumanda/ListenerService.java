package com.mitturkeyhack.kumanda;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.IBinder;
import android.os.PowerManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Alıcı telefonda sürekli çalışır: Kumanda'dan gelen komutları internetten dinler,
 * uygular ve cevabı geri gönderir. Bildirim çubuğunda "Uzaktan kontrol açık" görünür.
 */
public class ListenerService extends Service {

    private static final String CH = "kumanda_alici";
    private static final long MAX_AGE = 15 * 60; // 15 dakikadan eski komutlar uygulanmaz

    private volatile boolean running;
    private volatile HttpURLConnection conn;
    private Thread th;

    // Ses tuşlarına basılınca ses tavanını uygula
    private final BroadcastReceiver volRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            Blocker.enforceCap(c);
        }
    };

    // Şarj %15'in altına inince Kumanda'ya haber ver
    private final BroadcastReceiver batRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            int lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            if (lvl < 0 || sc <= 0) return;
            int pct = Math.round(lvl * 100f / sc);
            boolean warned = P.bool(c, "bat_warned");
            if (plugged != 0 || pct >= 30) {
                if (warned) P.put(c, "bat_warned", false);
                return;
            }
            if (pct <= 15 && !warned) {
                P.put(c, "bat_warned", true);
                final String code = P.myCode(c);
                final String msg = "🔋 Şarj %" + pct + " — prize takılması lazım";
                new Thread(() -> Proto.post(Proto.replyTopic(code), Proto.pack(code, "ALERT", msg))).start();
            }
        }
    };

    public static void start(Context c) {
        try {
            c.startForegroundService(new Intent(c, ListenerService.class));
        } catch (Exception ignored) {
        }
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, ListenerService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CH, "Uzaktan kontrol", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);

        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CH)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Uzaktan kontrol açık")
                .setContentText("Bu telefon Kumanda uygulamasıyla yönetiliyor")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        startForeground(1, n);

        registerReceiver(volRx, new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
        registerReceiver(batRx, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        running = true;
        th = new Thread(this::loop, "kumanda-dinle");
        th.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!P.ROLE_ALICI.equals(P.role(this))) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        running = false;
        try {
            unregisterReceiver(volRx);
            unregisterReceiver(batRx);
        } catch (Exception ignored) {
        }
        HttpURLConnection c = conn;
        if (c != null) {
            try {
                c.disconnect();
            } catch (Exception ignored) {
            }
        }
        if (th != null) th.interrupt();
        setStatus("Durduruldu");
        super.onDestroy();
    }

    private void setStatus(String s) {
        P.put(this, "conn_status", s);
        P.put(this, "conn_time", System.currentTimeMillis());
    }

    private void loop() {
        int backoff = 3;
        while (running) {
            if (!P.ROLE_ALICI.equals(P.role(this))) {
                stopSelf();
                return;
            }
            String code = P.myCode(this);
            HttpURLConnection c = null;
            try {
                setStatus("Bağlanıyor…");
                long now = System.currentTimeMillis() / 1000L;
                long since = Math.max(P.lng(this, "last_time"), now - MAX_AGE);
                URL u = new URL(Proto.SERVER + Proto.topic(code) + "/json?since=" + since);
                c = (HttpURLConnection) u.openConnection();
                conn = c;
                c.setConnectTimeout(20000);
                c.setReadTimeout(100000); // sunucu ~45 sn'de bir "keepalive" yollar
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                setStatus("Bağlı ✓");
                backoff = 3;
                String line;
                while (running && (line = br.readLine()) != null) {
                    handleLine(code, line);
                }
            } catch (Exception e) {
                if (running) setStatus("İnternet yok, tekrar deneniyor…");
            } finally {
                if (c != null) {
                    try {
                        c.disconnect();
                    } catch (Exception ignored) {
                    }
                }
                conn = null;
            }
            if (!running) break;
            try {
                Thread.sleep(backoff * 1000L);
            } catch (InterruptedException ie) {
                break;
            }
            backoff = Math.min(backoff * 2, 60);
        }
    }

    private void handleLine(String code, String line) {
        try {
            JSONObject o = new JSONObject(line);
            if (P.ROLE_ALICI.equals(P.role(this))) P.put(this, "conn_time", System.currentTimeMillis());
            if (!"message".equals(o.optString("event"))) return;
            if (!P.markSeen(this, "seen_ids", o.optString("id"))) return;
            long t = o.optLong("time", 0);
            if (t > P.lng(this, "last_time")) P.put(this, "last_time", t);

            String[] cm = Proto.unpack(code, o.optString("message"), MAX_AGE);
            if (cm == null) return;

            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kumanda:komut");
            wl.acquire(30_000);
            try {
                Exec.Result r = Exec.run(this, cm[0], cm[1]);
                if (r.text != null) Proto.post(Proto.replyTopic(code), Proto.pack(code, "ACK", r.text));
                for (String[] x : r.extra) Proto.post(Proto.replyTopic(code), Proto.pack(code, x[0], x[1]));
                if (r.after != null) {
                    try {
                        Thread.sleep(800);
                    } catch (InterruptedException ignored) {
                    }
                    r.after.run();
                }
            } finally {
                if (wl.isHeld()) wl.release();
            }
        } catch (Exception ignored) {
        }
    }
}
