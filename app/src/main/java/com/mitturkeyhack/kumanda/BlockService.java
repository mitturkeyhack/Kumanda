package com.mitturkeyhack.kumanda;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Engelli bir uygulama öne gelince ana ekrana atar ve "şu an çalışmıyor" uyarısı gösterir.
 * Ayrıca TikTok/YouTube'da geçirilen süreyi sayar ve ses tavanını uygular.
 * Ekrandaki içerikleri okumaz; sadece hangi uygulamanın açık olduğuna bakar.
 */
public class BlockService extends AccessibilityService {

    private static volatile BlockService instance;
    static volatile String lastPkg;
    static volatile long lastEventAt;

    private final Handler h = new Handler(Looper.getMainLooper());
    private long lastKick;

    // süre sayımı
    private String curGroup;
    private long curSince;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            checkNow();
            Blocker.enforceCap(BlockService.this);
            h.postDelayed(this, 15_000);
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

    /**
     * Arka plandan ekran açmak için: erişilebilirlik hizmeti açıksa onun üzerinden
     * başlatır (Android 10+ arka plan kısıtlamasına takılmaz).
     */
    public static void launch(Context fallback, Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        BlockService s = instance;
        try {
            if (s != null) s.startActivity(i);
            else fallback.startActivity(i);
        } catch (Exception e) {
            try {
                fallback.startActivity(i);
            } catch (Exception ignored) {
            }
        }
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
        if (pkg == null) return;
        lastPkg = pkg.toString();
        lastEventAt = System.currentTimeMillis();
        try {
            handle(pkg.toString());
        } catch (Throwable t) {
            P.put(this, "crash", "engel hatası: " + t);
        }
    }

    private void checkNow() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && root.getPackageName() != null) {
                handle(root.getPackageName().toString());
            } else {
                account(null);
            }
        } catch (Exception ignored) {
        }
    }

    /** Önceki uygulamada geçen süreyi yazar, yenisini başlatır. */
    private void account(String pkg) {
        long now = SystemClock.elapsedRealtime();
        if (curGroup != null) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && pm.isInteractive()) {
                long d = Math.min(now - curSince, 30_000); // en fazla 30 sn (bir kontrol aralığı)
                Blocker.addUse(this, curGroup, (int) (d / 1000));
            }
        }
        curGroup = Blocker.group(pkg);
        curSince = now;
    }

    private void handle(String pkg) {
        if (!P.ROLE_ALICI.equals(P.role(this))) return;
        if (pkg.equals(getPackageName())) {
            account(null);
            return;
        }
        // Klavye, bildirim paneli vb. sistem pencereleri süreyi bölmesin
        if (pkg.equals("com.android.systemui") || pkg.contains("inputmethod")) return;
        account(pkg);
        final String label = Blocker.blockedLabel(this, pkg);
        if (label == null) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastKick < 1500) return;
        lastKick = now;
        account(null);
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
