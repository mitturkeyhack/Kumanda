package com.mitturkeyhack.kumanda;

import android.content.Context;

import java.util.Calendar;
import java.util.Locale;

/** Hangi uygulamanın şu an engelli olduğuna karar verir. */
public final class Blocker {

    public static final String K_TIKTOK = "block_tiktok";
    public static final String K_YOUTUBE = "block_youtube";
    public static final String K_NIGHT_ON = "night_on";
    public static final String K_NIGHT_START = "night_start"; // gece yarısından itibaren dakika
    public static final String K_NIGHT_END = "night_end";

    private Blocker() {}

    public static boolean isTikTok(String pkg) {
        String p = pkg.toLowerCase(Locale.ROOT);
        return p.contains("musically") || p.contains("ugc.trill") || p.contains("tiktok")
                || p.startsWith("com.ss.android.ugc.aweme");
    }

    public static boolean isYouTube(String pkg) {
        String p = pkg.toLowerCase(Locale.ROOT);
        return p.contains("youtube") && !p.contains("music");
    }

    public static boolean nightActive(Context c) {
        if (!P.bool(c, K_NIGHT_ON)) return false;
        int s = P.num(c, K_NIGHT_START, 23 * 60);
        int e = P.num(c, K_NIGHT_END, 7 * 60);
        if (s == e) return false;
        Calendar cal = Calendar.getInstance();
        int now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        if (s < e) return now >= s && now < e;
        return now >= s || now < e;
    }

    /** Engelliyse uygulamanın görünen adını, değilse null döner. */
    public static String blockedLabel(Context c, String pkg) {
        if (pkg == null) return null;
        boolean night = nightActive(c);
        if (isTikTok(pkg) && (night || P.bool(c, K_TIKTOK))) return "TikTok";
        if (isYouTube(pkg) && (night || P.bool(c, K_YOUTUBE))) return "YouTube";
        return null;
    }

    public static String hhmm(int minutes) {
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }
}
