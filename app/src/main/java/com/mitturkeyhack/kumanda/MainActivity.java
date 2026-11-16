package com.mitturkeyhack.kumanda;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Açılış: telefonun rolüne göre doğru ekrana yönlendirir; rol yoksa seçtirir. */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String role = P.role(this);
        if (P.ROLE_KUMANDA.equals(role)) {
            go(KumandaActivity.class);
            return;
        }
        if (P.ROLE_ALICI.equals(role)) {
            go(AliciActivity.class);
            return;
        }
        showChooser();
    }

    private void go(Class<?> cls) {
        startActivity(new Intent(this, cls));
        finish();
    }

    private void showChooser() {
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 20);
        root.setPadding(p, Ui.dp(this, 40), p, p);
        sv.addView(root);

        TextView t = Ui.text(this, "Kumanda", 30, Ui.TEXT, true);
        root.addView(t);
        TextView s = Ui.text(this, "Bu telefon ne olacak?", 16, Ui.MUTED, false);
        s.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 24));
        root.addView(s);

        root.addView(choice("🎮  Kumanda",
                "Diğer telefonları BU telefondan yöneteceğim.\n(Senin telefonun — A35)",
                Ui.BLUE, () -> {
                    P.setRole(this, P.ROLE_KUMANDA);
                    KumandaService.start(this);
                    go(KumandaActivity.class);
                }));

        root.addView(choice("📥  Alıcı",
                "Bu telefon uzaktan yönetilecek.\n(Annenin / babanın telefonu)",
                Ui.GREEN, () -> {
                    P.setRole(this, P.ROLE_ALICI);
                    P.myCode(this);
                    ListenerService.start(this);
                    go(AliciActivity.class);
                }));

        setContentView(sv);
    }

    private LinearLayout choice(String title, String desc, int color, Runnable onClick) {
        LinearLayout card = Ui.card(this);
        card.setBackground(Ui.round(this, color, 20));
        card.setPadding(Ui.dp(this, 20), Ui.dp(this, 22), Ui.dp(this, 20), Ui.dp(this, 22));
        card.setGravity(Gravity.START);
        card.addView(Ui.text(this, title, 22, 0xFFFFFFFF, true));
        TextView d = Ui.text(this, desc, 15, 0xE6FFFFFF, false);
        d.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(d);
        card.setClickable(true);
        card.setOnClickListener(v -> onClick.run());
        return card;
    }
}
