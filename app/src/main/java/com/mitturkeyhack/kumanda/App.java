package com.mitturkeyhack.kumanda;

import android.app.Application;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Uygulama çökerse sebebini kaydeder ("Durumu sor" cevabında görünür). */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append(new SimpleDateFormat("dd.MM HH:mm", Locale.ROOT).format(new Date())).append(' ');
                sb.append(e.toString());
                StackTraceElement[] st = e.getStackTrace();
                for (int i = 0; i < st.length && i < 4; i++) {
                    sb.append(" @").append(st[i].getClassName().replace("com.mitturkeyhack.kumanda.", ""))
                            .append('.').append(st[i].getMethodName()).append(':').append(st[i].getLineNumber());
                }
                getSharedPreferences("kumanda", MODE_PRIVATE).edit().putString("crash", sb.toString()).commit();
            } catch (Throwable ignored) {
            }
            if (prev != null) prev.uncaughtException(t, e);
        });
    }
}
