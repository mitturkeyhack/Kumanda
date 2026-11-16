package com.mitturkeyhack.kumanda;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kumanda (A35) tarafında arka planda çalışır: diğer telefonlardan gelen cevapları ve
 * uyarıları (ör. şarj azaldı) dinler, kaydeder, gerekirse bildirim gösterir.
 */
public class KumandaService extends Service {

    private static final String CH = "kumanda_dinleyici";
    private static final String ACT_RELOAD = "reload";
    private static final Handler H = new Handler(Looper.getMainLooper());

    /** Uygulama listesi gelince Kumanda ekranına haber verir. */
    public interface AppsListener {
        void onApps(Devices.Device d, List<String[]> apps); // {+/-, ad, paket}
    }

    static volatile AppsListener appsListener;

    private volatile boolean running;
    private volatile HttpURLConnection conn;
    private Thread th;
    private final Map<String, String[]> appParts = new HashMap<>();

    public static void start(Context c) {
        try {
            c.startForegroundService(new Intent(c, KumandaService.class));
        } catch (Exception ignored) {
        }
    }

    /** Telefon eklenince/silinince dinlenen kanalları yenile. */
    public static void reload(Context c) {
        try {
            c.startForegroundService(new Intent(c, KumandaService.class).setAction(ACT_RELOAD));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CH, "Kumanda dinleyici", NotificationManager.IMPORTANCE_MIN);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CH)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Kumanda açık")
                .setContentText("Telefonlardan gelen uyarılar dinleniyor")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        startForeground(2, n);
        running = true;
        th = new Thread(this::loop, "kumanda-cevap");
        th.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!P.ROLE_KUMANDA.equals(P.role(this))) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACT_RELOAD.equals(intent.getAction())) {
            final HttpURLConnection c = conn;
            if (c != null) new Thread(() -> {
                try {
                    c.disconnect();
                } catch (Exception ignored) {
                }
            }).start();
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
        HttpURLConnection c = conn;
        if (c != null) {
            try {
                c.disconnect();
            } catch (Exception ignored) {
            }
        }
        if (th != null) th.interrupt();
        super.onDestroy();
    }

    private void loop() {
        int backoff = 2;
        while (running) {
            if (!P.ROLE_KUMANDA.equals(P.role(this))) {
                stopSelf();
                return;
            }
            List<Devices.Device> devices = Devices.load(this);
            if (devices.isEmpty()) {
                sleep(5);
                continue;
            }
            Map<String, Devices.Device> byTopic = new HashMap<>();
            StringBuilder topics = new StringBuilder();
            for (Devices.Device d : devices) {
                String rt = Proto.replyTopic(d.code);
                byTopic.put(rt, d);
                if (topics.length() > 0) topics.append(',');
                topics.append(rt);
            }
            HttpURLConnection c = null;
            try {
                long now = System.currentTimeMillis() / 1000L;
                long since = Math.max(P.lng(this, "reply_time"), now - 2 * 3600);
                c = (HttpURLConnection) new URL(Proto.SERVER + topics + "/json?since=" + since).openConnection();
                conn = c;
                c.setConnectTimeout(20000);
                c.setReadTimeout(100000);
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                backoff = 2;
                String line;
                while (running && (line = br.readLine()) != null) {
                    handle(byTopic, line);
                }
            } catch (Exception ignored) {
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
            sleep(backoff);
            backoff = Math.min(backoff * 2, 30);
        }
    }

    private void sleep(int sec) {
        try {
            Thread.sleep(sec * 1000L);
        } catch (InterruptedException ignored) {
        }
    }

    private void handle(Map<String, Devices.Device> byTopic, String line) {
        try {
            JSONObject o = new JSONObject(line);
            if (!"message".equals(o.optString("event"))) return;
            if (!P.markSeen(this, "reply_seen", o.optString("id"))) return;
            long t = o.optLong("time", 0);
            if (t > P.lng(this, "reply_time")) P.put(this, "reply_time", t);
            Devices.Device d = byTopic.get(o.optString("topic"));
            if (d == null) return;
            String[] cm = Proto.unpack(d.code, o.optString("message"), 24 * 3600);
            if (cm == null) return;
            switch (cm[0]) {
                case "ACK":
                    KLog.add(this, "← " + d.name + ": " + cm[1]);
                    break;
                case "ALERT":
                    KLog.add(this, "⚠️ " + d.name + ": " + cm[1]);
                    Notifs.alert(this, d.name, cm[1]);
                    break;
                case "APPS":
                    onAppsPart(d, cm[1]);
                    break;
                default:
                    break;
            }
        } catch (Exception ignored) {
        }
    }

    /** Parça: "i/n\n+\tAd\tpaket\n..." */
    private void onAppsPart(final Devices.Device d, String body) {
        int nl = body.indexOf('\n');
        String head = nl >= 0 ? body.substring(0, nl) : body;
        String data = nl >= 0 ? body.substring(nl + 1) : "";
        String[] hn = head.split("/");
        int i = Integer.parseInt(hn[0]), n = Integer.parseInt(hn[1]);
        String[] parts = appParts.get(d.code);
        if (parts == null || parts.length != n) {
            parts = new String[n];
            appParts.put(d.code, parts);
        }
        parts[i - 1] = data;
        for (String p : parts) if (p == null) return;
        appParts.remove(d.code);

        final List<String[]> apps = new ArrayList<>();
        for (String p : parts) {
            for (String l : p.split("\n")) {
                String[] f = l.split("\t");
                if (f.length == 3) apps.add(f);
            }
        }
        H.post(() -> {
            AppsListener l = appsListener;
            if (l != null) l.onApps(d, apps);
        });
    }
}
