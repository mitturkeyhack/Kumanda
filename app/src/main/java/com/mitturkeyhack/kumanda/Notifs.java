package com.mitturkeyhack.kumanda;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** Bildirimler. */
final class Notifs {

    static final String CH_MSG = "kumanda_mesaj";
    static final String CH_ALERT = "kumanda_uyari";

    private Notifs() {}

    private static NotificationManager nm(Context c, String ch, String name) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel nc = new NotificationChannel(ch, name, NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(nc);
        return nm;
    }

    /** Alıcı telefonda: gelen mesajın bildirimi (ekran açılmazsa diye yedek). */
    static void message(Context c, String text, Intent open) {
        try {
            NotificationManager nm = nm(c, CH_MSG, "Kumanda mesajları");
            PendingIntent pi = PendingIntent.getActivity(c, 11, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(c, CH_MSG)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle("💬 Yeni mesaj")
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setContentIntent(pi)
                    .setFullScreenIntent(pi, true)
                    .setAutoCancel(true)
                    .build();
            nm.notify(11, n);
        } catch (Exception ignored) {
        }
    }

    /** Kumanda telefonda: diğer telefondan gelen uyarı (ör. şarj azaldı). */
    static void alert(Context c, String title, String text) {
        try {
            NotificationManager nm = nm(c, CH_ALERT, "Telefon uyarıları");
            Intent open = new Intent(c, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(c, 12, open, PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(c, CH_ALERT)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            nm.notify((int) (System.currentTimeMillis() & 0xFFFFFF), n);
        } catch (Exception ignored) {
        }
    }
}
