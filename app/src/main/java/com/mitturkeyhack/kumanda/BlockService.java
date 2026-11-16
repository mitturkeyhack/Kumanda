package com.mitturkeyhack.kumanda;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Engelli bir uygulama (TikTok / YouTube) öne gelince ana ekrana atar ve
 * "şu an çalışmıyor" uyarısı gösterir. Ekrandaki içerikleri okumaz; sadece
 * hangi uygulamanın açık olduğuna bakar.
 */
public class BlockService extends AccessibilityService {

    private static volatile BlockService instance;

    private final Handler h = new Handler(Looper.getMainLooper());
    private long lastKick;

    // Uygulama zaten açıkken engel başlarsa (ör. gece 23:00) yakalamak için düzenli kontrol
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            checkNow();
            h.postDelayed(this, 20_000);
        }
    };

    public static boolean isRunning() {
        return instance != null;
    }

    /** Komut gelince hemen kontrol et. */
    public static void poke() {
        final BlockService s = instance;
        if (s != null) s.h.post(s::checkNow);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        h.removeCallbacks(tick);
        h.post(tick);
        if (P.ROLE_ALICI.equals(P.role(this))) ListenerService.start(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = e.getPackageName();
        if (pkg != null) handle(pkg.toString());
    }

    private void checkNow() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && root.getPackageName() != null) {
                handle(root.getPackageName().toString());
            }
        } catch (Exception ignored) {
        }
    }

    private void handle(String pkg) {
        if (!P.ROLE_ALICI.equals(P.role(this))) return;
        if (pkg.equals(getPackageName())) return;
        final String label = Blocker.blockedLabel(this, pkg);
        if (label == null) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastKick < 1500) return;
        lastKick = now;
        performGlobalAction(GLOBAL_ACTION_HOME);
        h.postDelayed(() -> {
            try {
                Intent i = new Intent(this, BlockedActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                i.putExtra("app", label);
                startActivity(i);
            } catch (Exception ignored) {
            }
        }, 350);
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        h.removeCallbacks(tick);
        instance = null;
        super.onDestroy();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        h.removeCallbacks(tick);
        instance = null;
        return super.onUnbind(intent);
    }
}
