package com.mitturkeyhack.kumanda;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** A35 ana ekranı için tek dokunuşluk kumanda. Komutlar TÜM telefonlara gider. */
public class KumandaWidget extends AppWidgetProvider {

    static final String PFX = "com.mitturkeyhack.kumanda.W_";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
            v.setOnClickPendingIntent(R.id.w_block, pi(c, "BLOCK"));
            v.setOnClickPendingIntent(R.id.w_free, pi(c, "FREE"));
            v.setOnClickPendingIntent(R.id.w_vol, pi(c, "VOL"));
            v.setOnClickPendingIntent(R.id.w_lock, pi(c, "LOCK"));
            v.setOnClickPendingIntent(R.id.w_title, PendingIntent.getActivity(c, 99,
                    new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
            m.updateAppWidget(id, v);
        }
    }

    private static PendingIntent pi(Context c, String what) {
        Intent i = new Intent(c, KumandaWidget.class).setAction(PFX + what);
        return PendingIntent.getBroadcast(c, what.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        String a = i.getAction();
        if (a == null || !a.startsWith(PFX)) return;
        run(c, a.substring(PFX.length()));
    }

    /** Widget ve uzun basma kısayolları ortak kullanır. */
    static void run(Context c, String what) {
        switch (what) {
            case "BLOCK":
                Sender.sendAll(c, "BLOCK", "AL1", "TikTok + YouTube engelle");
                break;
            case "FREE":
                Sender.sendAll(c, "BLOCK", "AL0", "TikTok + YouTube serbest");
                break;
            case "VOL":
                Sender.sendAll(c, "VOL", "20", "Sesi kıs (%20)");
                break;
            case "LOCK":
                Sender.sendAll(c, "LOCK", "", "Ekranı kilitle");
                break;
            default:
                break;
        }
    }
}
