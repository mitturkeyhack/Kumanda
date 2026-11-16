package com.mitturkeyhack.kumanda;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;

/**
 * Yedek engel: Erişilebilirlik hizmeti çalışmasa bile "Kullanım erişimi" izniyle
 * öndeki uygulamayı bulur ve engelliyse kapatır. ListenerService içinde çalışır.
 */
final class Guard {

    static volatile String lastFg;     // en son öne gelen uygulama
    static volatile long lastFgAt;

    private Guard() {}

    static boolean usageOk(Context c) {
        try {
            AppOpsManager ao = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            int m = ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
            return m == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean overlayOk(Context c) {
        return Settings.canDrawOverlays(c);
    }

    /** Arka planda sürekli döner; running false olunca durur. */
    static void loop(Context c, ListenerService svc) {
        final Handler h = new Handler(Looper.getMainLooper());
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        long from = System.currentTimeMillis() - 60_000;
        int blockedPolls = 0;
        long lastKick = 0;
        UsageEvents.Event ev = new UsageEvents.Event();
        while (svc.isRunning()) {
            try {
                Thread.sleep(1500);
                if (!P.ROLE_ALICI.equals(P.role(c))) continue;
                if (pm == null || !pm.isInteractive() || usm == null || !usageOk(c)) {
                    blockedPolls = 0;
                    continue;
                }
                long now = System.currentTimeMillis();
                UsageEvents events = usm.queryEvents(from, now);
                from = now - 2000;
                while (events != null && events.hasNextEvent()) {
                    events.getNextEvent(ev);
                    if (ev.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        lastFg = ev.getPackageName();
                        lastFgAt = ev.getTimeStamp();
                    }
                }
                String fg = lastFg;
                if (fg == null || fg.equals(c.getPackageName())) {
                    blockedPolls = 0;
                    continue;
                }
                // Erişilebilirlik kapalıysa süreyi burada say
                if (!BlockService.isRunning()) Blocker.addUse(c, Blocker.group(fg), 1);

                final String label = Blocker.blockedLabel(c, fg);
                if (label == null) {
                    blockedPolls = 0;
                    continue;
                }
                blockedPolls++;
                // Önce erişilebilirliğe şans ver (o çalışıyorsa ~anında atar)
                if (BlockService.isRunning() && blockedPolls < 2) continue;
                if (now - lastKick < 2500) continue;
                lastKick = now;
                blockedPolls = 0;
                h.post(() -> kick(c, label));
            } catch (InterruptedException ie) {
                return;
            } catch (Throwable ignored) {
            }
        }
    }

    static void kick(Context c, String label) {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            BlockService.launch(c, home);
        } catch (Exception ignored) {
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent i = new Intent(c, BlockedActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            i.putExtra("app", label);
            BlockService.launch(c, i);
        }, 350);
    }
}
