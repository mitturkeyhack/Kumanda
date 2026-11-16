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
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Alıcı telefonda gelen komutu uygular. */
public final class Exec {

    public static final class Result {
        public final String text;    // null = tek cevap yok
        public final Runnable after; // cevap gönderildikten SONRA yapılacak iş (ör. Wi-Fi kapatma)
        public final List<String[]> extra = new ArrayList<>(); // ek cevaplar: {komut, içerik}

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
        c0 = c.getApplicationContext();
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
                case "BLOCKSET":
                    return blockSet(c, arg);
                case "APPS":
                    return apps(c);
                case "LIMIT":
                    return limit(c, arg);
                case "CAP":
                    return cap(c, arg);
                case "MSG":
                    return message(c, arg);
                case "RING": {
                    int sec = 30;
                    try {
                        sec = Integer.parseInt(arg);
                    } catch (Exception ignored) {
                    }
                    Ringer.start(c, sec);
                    return ok("Telefon çalıyor 🔔 (" + sec + " sn)");
                }
                case "LOWBAT": {
                    int pct = 5;
                    try {
                        pct = Integer.parseInt(arg);
                    } catch (Exception ignored) {
                    }
                    Intent i = new Intent(c, BlockedActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    i.putExtra("title", "Pil seviyesi düşük");
                    i.putExtra("msg", "%" + pct + " kaldı.\nŞarj cihazını takın.");
                    BlockService.launch(c, i);
                    return ok("Şarj az uyarısı gösterildi 🪫 (%" + pct + ")");
                }
                case "RING_STOP":
                    Ringer.stop(null);
                    return ok("Çalma durduruldu");
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
        if (Blocker.enforceCap(c)) {
            return ok("Ses tavanı açık: ses %" + P.num(c, Blocker.K_CAP_PCT, 30) + "'de tutuldu");
        }
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
        if (arg.startsWith("AL")) {
            P.put(c, Blocker.K_TIKTOK, on);
            P.put(c, Blocker.K_YOUTUBE, on);
            app = "TikTok ve YouTube";
        } else if (arg.startsWith("TT")) {
            P.put(c, Blocker.K_TIKTOK, on);
            app = "TikTok";
        } else {
            P.put(c, Blocker.K_YOUTUBE, on);
            app = "YouTube";
        }
        String warn = "";
        if (on) {
            BlockService.poke(); // şu an açıksa hemen kapat
            warn = warnAcc();
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
        String warn = warnAcc();
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
        for (String g : new String[]{"TT", "YT"}) {
            int lim = Blocker.limitMin(c, g);
            if (lim > 0) {
                sb.append(", ").append(Blocker.groupName(g)).append(" bugün ")
                        .append(Blocker.usedSec(c, g) / 60).append("/").append(lim).append(" dk");
            }
        }
        Set<String> cs = Blocker.custom(c);
        if (!cs.isEmpty()) sb.append(", ").append(cs.size()).append(" uygulama daha engelli");
        if (P.bool(c, Blocker.K_CAP_ON)) {
            sb.append(", ses tavanı %").append(P.num(c, Blocker.K_CAP_PCT, 30));
            if (!P.bool(c, Blocker.K_CAP_ALL)) {
                sb.append(" (").append(Blocker.hhmm(P.num(c, Blocker.K_CAP_S, 0))).append("–")
                        .append(Blocker.hhmm(P.num(c, Blocker.K_CAP_E, 0))).append(")");
            }
        }
        // --- Teşhis ---
        sb.append("\n🔧 Engel: erişilebilirlik ").append(BlockService.isRunning() ? "✓" : (accessibilityOn(c) ? "AÇIK AMA ÇALIŞMIYOR ✗" : "kapalı ✗"));
        sb.append(", kullanım erişimi ").append(Guard.usageOk(c) ? "✓" : "✗");
        sb.append(", üstte gösterme ").append(Guard.overlayOk(c) ? "✓" : "✗");
        String lp = BlockService.lastPkg;
        if (lp == null) lp = Guard.lastFg;
        if (lp != null) sb.append("\nSon açık uygulama: ").append(lp);
        sb.append("\nYüklü: ").append(installedTargets(c));
        String crash = P.str(c, "crash", null);
        if (crash != null) sb.append("\nSon hata: ").append(crash.length() > 300 ? crash.substring(0, 300) : crash);
        return sb.toString();
    }

    private static Context c0;

    private static String warnAcc() {
        if (BlockService.isRunning() || Guard.usageOk(c0)) return "";
        return " (UYARI: Erişilebilirlik çalışmıyor ve yedek engel izni yok — engel çalışmaz!)";
    }

    private static Result blockSet(Context c, String arg) {
        StringBuilder clean = new StringBuilder();
        List<String> names = new ArrayList<>();
        if (arg != null) {
            for (String p : arg.split(",")) {
                p = p.trim();
                if (p.isEmpty()) continue;
                if (clean.length() > 0) clean.append(',');
                clean.append(p);
                names.add(Blocker.label(c, p));
            }
        }
        P.put(c, Blocker.K_CUSTOM, clean.toString());
        BlockService.poke();
        if (names.isEmpty()) return ok("Diğer uygulamaların engeli kaldırıldı ✅");
        return ok("Engellenen uygulamalar: " + String.join(", ", names) + " ⛔" + warnAcc());
    }

    /** Telefondaki uygulamaların listesini parça parça gönderir. */
    private static Result apps(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
        Set<String> blocked = Blocker.custom(c);
        List<String> lines = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (ResolveInfo ri : list) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || seen.contains(pkg)) continue;
            seen.add(pkg);
            String label = ri.loadLabel(pm).toString().replace('\t', ' ').replace('\n', ' ').replace('|', '/');
            lines.add((blocked.contains(pkg) ? "+" : "-") + "\t" + label + "\t" + pkg);
        }
        Collections.sort(lines, (a, b) -> a.substring(2).compareToIgnoreCase(b.substring(2)));
        List<String> chunks = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String l : lines) {
            if (cur.length() > 0 && cur.toString().getBytes(StandardCharsets.UTF_8).length + l.length() * 2 > 2600) {
                chunks.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append('\n');
            cur.append(l);
        }
        if (cur.length() > 0 || chunks.isEmpty()) chunks.add(cur.toString());
        Result r = new Result(null, null);
        for (int i = 0; i < chunks.size(); i++) {
            r.extra.add(new String[]{"APPS", (i + 1) + "/" + chunks.size() + "\n" + chunks.get(i)});
        }
        return r;
    }

    private static Result limit(Context c, String arg) {
        // arg: "TT,60" / "YT,0"
        String[] p = arg.split(",");
        String g = "TT".equals(p[0]) ? "TT" : "YT";
        int min = Integer.parseInt(p[1]);
        P.put(c, "limit_" + g, min);
        BlockService.poke();
        String name = Blocker.groupName(g);
        if (min <= 0) return ok(name + " süre sınırı kaldırıldı");
        int used = Blocker.usedSec(c, g) / 60;
        return ok(name + " günde " + dur(min) + " ile sınırlandı ⏱️ (bugün " + used + " dk kullanıldı)" + warnAcc());
    }

    private static Result cap(Context c, String arg) {
        // arg: "OFF" / "30,ALL" / "30,1320,420"
        if (arg == null || arg.equals("OFF")) {
            P.put(c, Blocker.K_CAP_ON, false);
            return ok("Ses tavanı kaldırıldı");
        }
        String[] p = arg.split(",");
        int pct = Integer.parseInt(p[0]);
        P.put(c, Blocker.K_CAP_PCT, pct);
        P.put(c, Blocker.K_CAP_ON, true);
        String when;
        if (p.length >= 3) {
            int s = Integer.parseInt(p[1]), e = Integer.parseInt(p[2]);
            P.put(c, Blocker.K_CAP_ALL, false);
            P.put(c, Blocker.K_CAP_S, s);
            P.put(c, Blocker.K_CAP_E, e);
            when = Blocker.hhmm(s) + "–" + Blocker.hhmm(e) + " arası";
        } else {
            P.put(c, Blocker.K_CAP_ALL, true);
            when = "her zaman";
        }
        Blocker.enforceCap(c);
        return ok("Ses tavanı: " + when + " en fazla %" + pct + " 🔉");
    }

    private static Result message(Context c, String arg) {
        // arg: "1\nmetin" (1 = sesli oku)
        boolean speak = arg.startsWith("1");
        int nl = arg.indexOf('\n');
        String text = nl >= 0 ? arg.substring(nl + 1) : arg;
        Intent i = new Intent(c, MessageActivity.class);
        i.putExtra("text", text);
        i.putExtra("speak", speak);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        BlockService.launch(c, i);
        Notifs.message(c, text, i);
        return ok("Mesaj ekranda gösterildi 💬" + (speak ? " (sesli okundu)" : ""));
    }

    private static String installedTargets(Context c) {
        StringBuilder sb = new StringBuilder();
        try {
            for (android.content.pm.ApplicationInfo ai : c.getPackageManager().getInstalledApplications(0)) {
                String g = Blocker.group(ai.packageName);
                if (g == null) continue;
                if (sb.length() > 0) sb.append(", ");
                sb.append(ai.packageName);
            }
        } catch (Exception ignored) {
        }
        return sb.length() == 0 ? "TikTok/YouTube bulunamadı" : sb.toString();
    }

    @SuppressWarnings("unused")
    static boolean accessibilityOn(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.toLowerCase(java.util.Locale.ROOT).contains(c.getPackageName().toLowerCase(java.util.Locale.ROOT) + "/");
    }
}
