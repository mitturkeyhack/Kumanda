package com.mitturkeyhack.kumanda;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Telefon çaldırılırken çıkan "Durdur" ekranı. */
public class RingActivity extends Activity {

    private static RingActivity current;

    static void closeAll() {
        RingActivity a = current;
        if (a != null) a.finish();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        current = this;
        Screen.wake(this);

        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setBackgroundColor(0xFF1E3A8A);
        int p = Ui.dp(this, 28);
        l.setPadding(p, p, p, p);

        TextView icon = Ui.text(this, "🔔", 72, Color.WHITE, false);
        icon.setGravity(Gravity.CENTER);
        l.addView(icon);
        TextView t = Ui.text(this, "Telefon çaldırılıyor", 28, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 6));
        l.addView(t);
        TextView s = Ui.text(this, "Kumanda bu telefonu buluyor.", 17, 0xCCFFFFFF, false);
        s.setGravity(Gravity.CENTER);
        l.addView(s);

        Button stop = Ui.btn(this, "Durdur", Ui.RED);
        stop.setTextSize(22);
        stop.setMinHeight(Ui.dp(this, 72));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 40), 0, 0);
        stop.setOnClickListener(v -> {
            Ringer.stop(null);
            finish();
        });
        l.addView(stop, lp);
        setContentView(l);
    }

    @Override
    protected void onDestroy() {
        if (current == this) current = null;
        super.onDestroy();
    }
}
