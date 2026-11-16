package com.mitturkeyhack.kumanda;

import android.app.Activity;
import android.os.Build;
import android.view.WindowManager;

/** Kilit ekranının üstünde görünüp ekranı açmak için. */
final class Screen {
    private Screen() {}

    @SuppressWarnings("deprecation")
    static void wake(Activity a) {
        if (Build.VERSION.SDK_INT >= 27) {
            a.setShowWhenLocked(true);
            a.setTurnScreenOn(true);
        }
        a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}
