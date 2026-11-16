package com.mitturkeyhack.kumanda;

import android.content.Context;
import android.content.SharedPreferences;

/** Uygulama ayarları. */
public final class P {

    public static final String ROLE_KUMANDA = "kumanda";
    public static final String ROLE_ALICI = "alici";

    private P() {}

    public static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("kumanda", Context.MODE_PRIVATE);
    }

    public static String role(Context c) {
        return sp(c).getString("role", null);
    }

    public static void setRole(Context c, String r) {
        sp(c).edit().putString("role", r).apply();
    }

    /** Alıcı telefonun eşleşme kodu (yoksa üretir). */
    public static String myCode(Context c) {
        String code = sp(c).getString("my_code", null);
        if (code == null) {
            code = Proto.newCode();
            sp(c).edit().putString("my_code", code).apply();
        }
        return code;
    }

    public static String str(Context c, String k, String def) {
        return sp(c).getString(k, def);
    }

    public static void put(Context c, String k, String v) {
        sp(c).edit().putString(k, v).apply();
    }

    public static boolean bool(Context c, String k) {
        return sp(c).getBoolean(k, false);
    }

    public static void put(Context c, String k, boolean v) {
        sp(c).edit().putBoolean(k, v).apply();
    }

    public static int num(Context c, String k, int def) {
        return sp(c).getInt(k, def);
    }

    public static void put(Context c, String k, int v) {
        sp(c).edit().putInt(k, v).apply();
    }

    public static long lng(Context c, String k) {
        return sp(c).getLong(k, 0L);
    }

    public static void put(Context c, String k, long v) {
        sp(c).edit().putLong(k, v).apply();
    }

    /** Aynı mesajı iki kez işlememek için son mesaj kimliklerini tutar. true = yeni. */
    public static synchronized boolean markSeen(Context c, String key, String id) {
        if (id == null || id.isEmpty()) return true;
        String s = sp(c).getString(key, "");
        String[] arr = s.isEmpty() ? new String[0] : s.split(",");
        for (String x : arr) if (x.equals(id)) return false;
        StringBuilder sb = new StringBuilder(id);
        int n = 0;
        for (int i = arr.length - 1; i >= 0 && n < 40; i--, n++) {
            sb.insert(0, arr[i] + ",");
        }
        sp(c).edit().putString(key, sb.toString()).apply();
        return true;
    }
}
