package com.mitturkeyhack.kumanda;

import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/** Kumanda'dan gelen mesajı tam ekran gösterir, istenirse sesli okur. */
public class MessageActivity extends Activity {

    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Screen.wake(this);
        getWindow().setStatusBarColor(0xFFFFFBEB);

        final String text = getIntent().getStringExtra("text");
        final boolean speak = getIntent().getBooleanExtra("speak", false);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(0xFFFFFBEB);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        int p = Ui.dp(this, 28);
        l.setPadding(p, p, p, p);
        sv.addView(l);

        TextView icon = Ui.text(this, "💬", 64, Ui.TEXT, false);
        icon.setGravity(Gravity.CENTER);
        l.addView(icon);
        TextView from = Ui.text(this, "Yeni mesaj", 16, Ui.MUTED, true);
        from.setGravity(Gravity.CENTER);
        from.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 16));
        l.addView(from);
        TextView msg = Ui.text(this, text == null ? "" : text, 32, Ui.TEXT, true);
        msg.setGravity(Gravity.CENTER);
        msg.setLineSpacing(0, 1.1f);
        l.addView(msg);

        Button ok = Ui.btn(this, "Tamam", Ui.BLUE);
        ok.setTextSize(20);
        ok.setMinHeight(Ui.dp(this, 64));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 36), 0, 0);
        ok.setOnClickListener(v -> finish());
        l.addView(ok, lp);

        if (speak && text != null && !text.isEmpty()) {
            Button again = Ui.flatBtn(this, "🔊 Tekrar oku", Ui.BLUE);
            again.setOnClickListener(v -> say(text));
            l.addView(again);
            tts = new TextToSpeech(getApplicationContext(), st -> {
                if (st == TextToSpeech.SUCCESS) {
                    try {
                        tts.setLanguage(new Locale("tr", "TR"));
                    } catch (Exception ignored) {
                    }
                    say(text);
                }
            });
        }
        setContentView(sv);
    }

    private void say(String text) {
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "msg");
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
