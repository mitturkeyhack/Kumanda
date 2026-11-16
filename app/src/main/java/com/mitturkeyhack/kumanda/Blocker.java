package com.mitturkeyhack.kumanda;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Alıcı telefondaki kurallar: engeller, gece engeli, süre sınırı, ses tavanı. */
public final class Blocker {

    public static final String K_TIKTOK = "block_tiktok";
    public static final String K_YOUTUBE = "block_youtube";
    public static final String K_NIGHT_ON = "night_on";
    public static final String K_NIGHT_START = "night_start"; // gece yarısından itibaren dakika
    public static final String K_NIGHT_END = "night_end";
    public static final String K_CUSTOM = "block_custom";     // virgülle ayrılmış paket adları
    public static final String K_LIMIT_TT = "limit_TT";       // dakika, 0 = sınır yok
    public static final String K_LIMIT_YT = "limit_YT";
    public static final String K_CAP_ON = "cap_on";
    public static final String K_CAP_PCT = "cap_pct";
    public static final String K_CAP_ALL = "cap_all";
    public static final String K_CAP_S = "cap_s";
    public static final String K_CAP_E = "cap_e";

    private Blocker() {}

    // ---------- Uygulama grupları ----------

    public static boolean isTikTok(String pkg) {
        String p = pkg.toLowerCase(Locale.ROOT);
        return p.contains("musically") || p.contains("ugc.trill") || p.contains("tiktok")
                || p.startsWith("com.ss.android.ugc.aweme");
    }

    public static boolean isYouTube(String pkg) {
        String p = pkg.toLowerCase(Locale.ROOT);
        return p.contains("youtube") && !p.contains("music");
    }

    /** "TT", "YT" ya da null. */
    public static String group(String pkg) {
        if (pkg == null) return null;
        if (isTikTok(pkg)) return "TT";
        if (isYouTube(pkg)) return "YT";
        return null;
    }

    public static String groupName(String g) {
        return "TT".equals(g) ? "TikTok" : "YouTube";
    }

    // ---------- Saat aralığı ----------

    public static int nowMinutes() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
    }

    public static boolean inWindow(int s, int e) {
        if (s == e) return false;
        int now = nowMinutes();
        if (s < e) return now >= s && now < e;
        return now >= s || now < e;
    }

    public static boolean nightActive(Context c) {
        if (!P.bool(c, K_NIGHT_ON)) return false;
        return inWindow(P.num(c, K_NIGHT_START, 23 * 60), P.num(c, K_NIGHT_END, 7 * 60));
    }

    // ---------- Özel engel listesi ----------

    public static Set<String> custom(Context c) {
        Set<String> s = new HashSet<>();
        String v = P.str(c, K_CUSTOM, "");
        for (String x : v.split(",")) {
            x = x.trim();
            if (!x.isEmpty()) s.add(x);
        }
        return s;
    }

    public static String label(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    // ---------- Günlük süre ----------

    private static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date());
    }

    private static void rollDay(Context c) {
        String t = today();
        if (!t.equals(P.str(c, "use_day", ""))) {
            P.sp(c).edit().putString("use_day", t).putInt("use_TT", 0).putInt("use_YT", 0).apply();
        }
    }

    /** Bugün kullanılan saniye. */
    public static int usedSec(Context c, String g) {
        rollDay(c);
        return P.num(c, "use_" + g, 0);
    }

    public static void addUse(Context c, String g, int sec) {
        if (g == null || sec <= 0) return;
        rollDay(c);
        P.put(c, "use_" + g, P.num(c, "use_" + g, 0) + sec);
    }

    public static int limitMin(Context c, String g) {
        return P.num(c, "limit_" + g, 0);
    }

    public static boolean limitReached(Context c, String g) {
        int lim = limitMin(c, g);
        return lim > 0 && usedSec(c, g) >= lim * 60;
    }

    // ---------- Karar ----------

    /** Engelliyse uygulamanın görünen adını, değilse null döner. */
    public static String blockedLabel(Context c, String pkg) {
        if (pkg == null) return null;
        String g = group(pkg);
        if (g != null) {
            boolean manual = P.bool(c, "TT".equals(g) ? K_TIKTOK : K_YOUTUBE);
            if (manual || nightActive(c) || limitReached(c, g)) return groupName(g);
        }
        if (custom(c).contains(pkg)) return label(c, pkg);
        return null;
    }

    // ---------- Ses tavanı ----------

    public static boolean capActive(Context c) {
        if (!P.bool(c, K_CAP_ON)) return false;
        if (P.bool(c, K_CAP_ALL)) return true;
        return inWindow(P.num(c, K_CAP_S, 22 * 60), P.num(c, K_CAP_E, 7 * 60));
    }

    /** Ses tavanı aktifse ve ses yüksekse aşağı çeker. true = değiştirdi. */
    public static boolean enforceCap(Context c) {
        try {
            if (!capActive(c)) return false;
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int capV = Math.round(max * P.num(c, K_CAP_PCT, 30) / 100f);
            if (am.getStreamVolume(AudioManager.STREAM_MUSIC) > capV) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, capV, 0);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static String hhmm(int minutes) {
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }
}
