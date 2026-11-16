package com.mitturkeyhack.kumanda;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Kumanda ekranındaki "gelen cevaplar" kaydı. */
public final class KLog {

    private static final Handler H = new Handler(Looper.getMainLooper());
    static volatile Runnable listener;

    private KLog() {}

    public static synchronized void add(Context c, String s) {
        try {
            JSONArray a = new JSONArray(P.str(c, "log", "[]"));
            String time = new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date());
            JSONArray n = new JSONArray();
            n.put(time + "  " + s);
            for (int i = 0; i < a.length() && i < 39; i++) n.put(a.getString(i));
            P.put(c, "log", n.toString());
        } catch (Exception ignored) {
        }
        Runnable l = listener;
        if (l != null) H.post(l);
    }
}
