package com.mitturkeyhack.kumanda;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.provider.Settings;

/** Alıcı telefonda gelen komutu uygular. */
public final class Exec {

    public static final class Result {
        public final String text;
        public final Runnable after; // cevap gönderildikten SONRA yapılacak iş (ör. Wi-Fi kapatma)

        Result(String text, Runnable after) {
            this.text = text;
            this.after = after;
        }
    }

    private Exec() {}

    private static Result ok(String t) {
        return new Result(t, null);
    }

    public static Result run(final Context c, String cmd, String arg) {
        try {
            switch (cmd) {
                case "PING":
                    return ok("Bağlantı tamam ✓");
                case "VOL":
                    return volume(c, arg);
                case "WIFI_ON":
                    cancelWifiAlarm(c);
                    return ok(wifi(c).setWifiEnabled(true) ? "Wi‑Fi açıldı 📶" : "Wi‑Fi açılamadı (izin verilmemiş olabilir)");
                case "WIFI_OFF":
                    return wifiOff(c, arg);
                case "BT_ON": {
                    BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
                    if (a == null) return ok("Bu telefonda Bluetooth yok");
                    return ok(a.isEnabled() || a.enable() ? "Bluetooth açıldı 🔵" : "Bluetooth açılamadı (izin?)");
                }
                case "BT_OFF": {
                    BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
                    if (a == null) return ok("Bu telefonda Bluetooth yok");
                    return ok(!a.isEnabled() || a.disable() ? "Bluetooth kapatıldı" : "Bluetooth kapatılamadı (izin?)");
                }
                case "LOCK":
                    return lock(c);
                case "BLOCK":
                    return block(c, arg);
                case "NIGHT":
                    return night(c, arg);
                case "STATUS":
                    return ok(status(c));
                default:
                    return ok("Bilinmeyen komut: " + cmd + " (uygulamayı güncelle)");
            }
        } catch (Exception e) {
            return ok("Hata: " + e.getClass().getSimpleName());
        }
    }

    private static WifiManager wifi(Context c) {
        return (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    private static Result volume(Context c, String arg) {
        int pct = 0;
        try {
            pct = Integer.parseInt(arg);
        } catch (Exception ignored) {
        }
        pct = Math.max(0, Math.min(100, pct));
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int v = Math.round(max * pct / 100f);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0);
        return ok(pct == 0 ? "Ses kapatıldı 🔇" : "Ses %" + pct + " yapıldı 🔊");
    }

    private static Result wifiOff(final Context c, String arg) {
        int min = 0;
        try {
            min = Integer.parseInt(arg);
        } catch (Exception ignored) {
        }
        final int minutes = min;
        String t = "Wi‑Fi kapatılıyor";
        if (minutes > 0) t += " — " + dur(minutes) + " sonra kendiliğinden açılacak";
        else t += " (süresiz)";
        return new Result(t, () -> {
            if (minutes > 0) {
                AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
                long at = System.currentTimeMillis() + minutes * 60_000L;
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, wifiAlarm(c));
            } else {
                cancelWifiAlarm(c);
            }
            wifi(c).setWifiEnabled(false);
        });
    }

    static PendingIntent wifiAlarm(Context c) {
        Intent i = new Intent(c, WifiOnReceiver.class);
        return PendingIntent.getBroadcast(c, 7, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void cancelWifiAlarm(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(wifiAlarm(c));
    }

    static String dur(int minutes) {
        if (minutes < 60) return minutes + " dk";
        int h = minutes / 60, m = minutes % 60;
        return m == 0 ? h + " saat" : h + " saat " + m + " dk";
    }

    private static Result lock(Context c) {
        DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(c, AdminReceiver.class);
        if (!dpm.isAdminActive(admin)) return ok("Ekran kilitleme izni verilmemiş (Alıcı ekranından aç)");
        dpm.lockNow();
        return ok("Ekran kilitlendi 🔒");
    }

    private static Result block(Context c, String arg) {
        // arg: TT1 / TT0 / YT1 / YT0
        if (arg == null || arg.length() != 3) return ok("Geçersiz engel komutu");
        boolean on = arg.charAt(2) == '1';
        String app;
        if (arg.startsWith("TT")) {
            P.put(c, Blocker.K_TIKTOK, on);
            app = "TikTok";
        } else {
            P.put(c, Blocker.K_YOUTUBE, on);
            app = "YouTube";
        }
        String warn = "";
        if (on) {
            BlockService.poke(); // şu an açıksa hemen kapat
            if (!BlockService.isRunning()) warn = " (UYARI: Erişilebilirlik izni kapalı, engel çalışmaz!)";
        }
        return ok(app + (on ? " engellendi ⛔" : " serbest bırakıldı ✅") + warn);
    }

    private static Result night(Context c, String arg) {
        if (arg == null || arg.equals("OFF")) {
            P.put(c, Blocker.K_NIGHT_ON, false);
            return ok("Gece engeli kapatıldı");
        }
        String[] p = arg.split("-");
        int s = Integer.parseInt(p[0]), e = Integer.parseInt(p[1]);
        P.put(c, Blocker.K_NIGHT_START, s);
        P.put(c, Blocker.K_NIGHT_END, e);
        P.put(c, Blocker.K_NIGHT_ON, true);
        BlockService.poke();
        String warn = BlockService.isRunning() ? "" : " (UYARI: Erişilebilirlik izni kapalı!)";
        return ok("Gece engeli: her gün " + Blocker.hhmm(s) + " – " + Blocker.hhmm(e)
                + " arası TikTok ve YouTube kapalı 🌙" + warn);
    }

    private static String status(Context c) {
        StringBuilder sb = new StringBuilder("Durum: ");
        WifiManager w = wifi(c);
        sb.append("Wi‑Fi ").append(w.isWifiEnabled() ? "açık" : "kapalı");
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        if (a != null) sb.append(", Bluetooth ").append(a.isEnabled() ? "açık" : "kapalı");
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        sb.append(", ses %").append(max == 0 ? 0 : Math.round(cur * 100f / max));
        Intent bat = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bat != null) {
            int lvl = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int sc = bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (lvl >= 0) sb.append(", şarj %").append(Math.round(lvl * 100f / sc));
        }
        sb.append(", TikTok ").append(P.bool(c, Blocker.K_TIKTOK) ? "engelli" : "serbest");
        sb.append(", YouTube ").append(P.bool(c, Blocker.K_YOUTUBE) ? "engelli" : "serbest");
        if (P.bool(c, Blocker.K_NIGHT_ON)) {
            sb.append(", gece engeli ")
                    .append(Blocker.hhmm(P.num(c, Blocker.K_NIGHT_START, 0))).append("–")
                    .append(Blocker.hhmm(P.num(c, Blocker.K_NIGHT_END, 0)));
            if (Blocker.nightActive(c)) sb.append(" (şu an aktif)");
        }
        if (!BlockService.isRunning()) sb.append(" ⚠ Erişilebilirlik kapalı");
        return sb.toString();
    }

    @SuppressWarnings("unused")
    static boolean accessibilityOn(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.toLowerCase(java.util.Locale.ROOT).contains(c.getPackageName().toLowerCase(java.util.Locale.ROOT) + "/");
    }
}
