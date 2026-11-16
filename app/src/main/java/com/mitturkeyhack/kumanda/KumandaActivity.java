package com.mitturkeyhack.kumanda;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A35'teki kumanda ekranı. */
public class KumandaActivity extends Activity {

    private static final class Device {
        String name;
        String code;

        Device(String n, String c) {
            name = n;
            code = c;
        }
    }

    private final Handler h = new Handler(Looper.getMainLooper());
    private final List<Device> devices = new ArrayList<>();
    private int selected = 0; // -1 = tümü

    private LinearLayout chips;
    private TextView logView;
    private TextView targetLabel;

    private volatile boolean streaming;
    private volatile HttpURLConnection streamConn;
    private Thread streamThread;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        loadDevices();
        selected = P.num(this, "selected", 0);
        build();
    }

    // ---------- Cihaz listesi ----------

    private void loadDevices() {
        devices.clear();
        try {
            JSONArray a = new JSONArray(P.str(this, "devices", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                devices.add(new Device(o.getString("name"), o.getString("code")));
            }
        } catch (Exception ignored) {
        }
    }

    private void saveDevices() {
        JSONArray a = new JSONArray();
        try {
            for (Device d : devices) {
                JSONObject o = new JSONObject();
                o.put("name", d.name);
                o.put("code", d.code);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        P.put(this, "devices", a.toString());
    }

    private List<Device> targets() {
        List<Device> t = new ArrayList<>();
        if (devices.isEmpty()) return t;
        if (selected < 0) return new ArrayList<>(devices);
        if (selected >= devices.size()) selected = 0;
        t.add(devices.get(selected));
        return t;
    }

    // ---------- Ekran ----------

    private void build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        root.setPadding(p, Ui.dp(this, 22), p, p);
        sv.addView(root);

        root.addView(Ui.text(this, "Kumanda", 28, Ui.TEXT, true));

        // Telefon seçici
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 8));
        hs.addView(chips);
        root.addView(hs);

        targetLabel = Ui.text(this, "", 13, Ui.MUTED, false);
        targetLabel.setPadding(Ui.dp(this, 2), 0, 0, Ui.dp(this, 10));
        root.addView(targetLabel);

        // Ses
        LinearLayout c1 = Ui.card(this);
        c1.addView(Ui.section(this, "🔊  Ses (medya)"));
        c1.addView(Ui.row(this,
                cmdBtn("🔇 Sesi kapat", Ui.RED, "VOL", "0"),
                cmdBtn("Kıs (%30)", Ui.SLATE, "VOL", "30")));
        c1.addView(Ui.row(this,
                cmdBtn("Orta (%60)", Ui.SLATE, "VOL", "60"),
                cmdBtn("Sonuna kadar", Ui.BLUE, "VOL", "100")));
        root.addView(c1);

        // Uygulamalar
        LinearLayout c2 = Ui.card(this);
        c2.addView(Ui.section(this, "⛔  Uygulama engeli"));
        c2.addView(Ui.row(this,
                cmdBtn("TikTok engelle", Ui.RED, "BLOCK", "TT1"),
                cmdBtn("TikTok serbest", Ui.GREEN, "BLOCK", "TT0")));
        c2.addView(Ui.row(this,
                cmdBtn("YouTube engelle", Ui.RED, "BLOCK", "YT1"),
                cmdBtn("YouTube serbest", Ui.GREEN, "BLOCK", "YT0")));
        Button night = Ui.btn(this, "🌙 Gece engeli ayarla", Ui.PURPLE);
        night.setOnClickListener(v -> askNight());
        c2.addView(Ui.row(this, night, cmdBtn("Gece engeli kapat", Ui.SLATE, "NIGHT", "OFF")));
        TextView nh = Ui.text(this, "Gece engeli her gün seçtiğin saatler arasında TikTok ve YouTube'u otomatik kapatır.", 12, Ui.MUTED, false);
        c2.addView(nh);
        root.addView(c2);

        // Bağlantı
        LinearLayout c3 = Ui.card(this);
        c3.addView(Ui.section(this, "📶  Bağlantı"));
        Button wOff = Ui.btn(this, "Wi‑Fi kapat…", Ui.RED);
        wOff.setOnClickListener(v -> askWifiOff());
        c3.addView(Ui.row(this, cmdBtn("Wi‑Fi aç", Ui.GREEN, "WIFI_ON", ""), wOff));
        c3.addView(Ui.row(this,
                cmdBtn("Bluetooth aç", Ui.GREEN, "BT_ON", ""),
                cmdBtn("Bluetooth kapat", Ui.RED, "BT_OFF", "")));
        root.addView(c3);

        // Diğer
        LinearLayout c4 = Ui.card(this);
        c4.addView(Ui.section(this, "📱  Telefon"));
        c4.addView(Ui.row(this,
                cmdBtn("🔒 Ekranı kilitle", Ui.ORANGE, "LOCK", ""),
                cmdBtn("ℹ️ Durumu sor", Ui.BLUE, "STATUS", "")));
        root.addView(c4);

        // Cevaplar
        LinearLayout c5 = Ui.card(this);
        c5.addView(Ui.section(this, "💬  Telefonlardan gelen cevaplar"));
        logView = Ui.text(this, "", 14, Ui.TEXT, false);
        logView.setLineSpacing(0, 1.15f);
        c5.addView(logView);
        Button clr = Ui.flatBtn(this, "Temizle", Ui.MUTED);
        clr.setOnClickListener(v -> {
            P.put(this, "log", "[]");
            renderLog();
        });
        c5.addView(clr);
        root.addView(c5);

        Button reset = Ui.flatBtn(this, "Rolü sıfırla", Ui.MUTED);
        reset.setOnClickListener(v -> confirmReset());
        root.addView(reset);

        setContentView(sv);
        renderChips();
        renderLog();
    }

    private Button cmdBtn(String label, int color, final String cmd, final String arg) {
        Button b = Ui.btn(this, label, color);
        b.setOnClickListener(v -> send(cmd, arg, label));
        return b;
    }

    private void renderChips() {
        chips.removeAllViews();
        if (selected >= devices.size()) selected = devices.isEmpty() ? 0 : devices.size() - 1;
        if (selected < 0 && devices.size() < 2) selected = 0;
        if (devices.size() >= 2) chips.addView(chip("👨‍👩‍👦 Tümü", selected < 0, -1));
        for (int i = 0; i < devices.size(); i++) {
            chips.addView(chip(devices.get(i).name, selected == i, i));
        }
        Button add = Ui.btn(this, "＋ Telefon ekle", Ui.TEXT);
        add.setMinHeight(Ui.dp(this, 44));
        add.setOnClickListener(v -> askAdd());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        add.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        chips.addView(add, lp);

        if (devices.isEmpty()) {
            targetLabel.setText("Önce annenin/babanın telefonunu ekle: o telefonda Alıcı ekranındaki kodu gir.");
        } else if (selected < 0) {
            targetLabel.setText("Komutlar TÜM telefonlara gidecek. (Silmek için ada basılı tut)");
        } else {
            targetLabel.setText("Komutlar \"" + devices.get(selected).name + "\" telefonuna gidecek. (Silmek için basılı tut)");
        }
    }

    private View chip(String name, boolean on, final int idx) {
        Button b = Ui.btn(this, name, on ? Ui.BLUE : 0xFFCBD5E1);
        b.setTextColor(on ? 0xFFFFFFFF : Ui.TEXT);
        b.setMinHeight(Ui.dp(this, 44));
        b.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
        b.setOnClickListener(v -> {
            selected = idx;
            P.put(this, "selected", idx);
            renderChips();
        });
        if (idx >= 0) {
            b.setOnLongClickListener(v -> {
                confirmDelete(idx);
                return true;
            });
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, Ui.dp(this, 8), 0);
        b.setLayoutParams(lp);
        return b;
    }

    // ---------- Diyaloglar ----------

    private void askAdd() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 20);
        l.setPadding(p, Ui.dp(this, 8), p, 0);
        final EditText name = new EditText(this);
        name.setHint("Ad (ör. Annem)");
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        l.addView(name);
        final EditText code = new EditText(this);
        code.setHint("Kod (ör. ABCD-EFGH-JKLM)");
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        l.addView(code);
        TextView t = Ui.text(this, "Kod, diğer telefonda Kumanda › Alıcı ekranında yazıyor.", 13, Ui.MUTED, false);
        t.setPadding(0, Ui.dp(this, 6), 0, 0);
        l.addView(t);

        final AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Telefon ekle")
                .setView(l)
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Ekle", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            String c = code.getText().toString().trim();
            if (n.isEmpty()) {
                name.setError("Bir ad yaz");
                return;
            }
            if (!Proto.validCode(c)) {
                code.setError("Kod 12 harf/rakam olmalı");
                return;
            }
            String nc = Proto.norm(c);
            String pretty = nc.substring(0, 4) + "-" + nc.substring(4, 8) + "-" + nc.substring(8, 12);
            devices.add(new Device(n, pretty));
            saveDevices();
            selected = devices.size() - 1;
            P.put(this, "selected", selected);
            renderChips();
            restartStream();
            d.dismiss();
            // Bağlantıyı dene
            sendTo(devices.get(selected), "PING", "", "Bağlantı testi");
        }));
        d.show();
    }

    private void confirmDelete(final int idx) {
        new AlertDialog.Builder(this)
                .setTitle(devices.get(idx).name + " silinsin mi?")
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Sil", (d, w) -> {
                    devices.remove(idx);
                    saveDevices();
                    selected = 0;
                    P.put(this, "selected", selected);
                    renderChips();
                    restartStream();
                })
                .show();
    }

    private void askWifiOff() {
        final String[] items = {"15 dakika", "1 saat", "Sabah 07:00'ye kadar", "Süresiz (dikkat!)"};
        new AlertDialog.Builder(this)
                .setTitle("Wi‑Fi ne kadar kapalı kalsın?")
                .setItems(items, (d, which) -> {
                    int min;
                    switch (which) {
                        case 0:
                            min = 15;
                            break;
                        case 1:
                            min = 60;
                            break;
                        case 2:
                            min = minutesUntil(7, 0);
                            break;
                        default:
                            min = 0;
                    }
                    if (min == 0) {
                        new AlertDialog.Builder(this)
                                .setTitle("Emin misin?")
                                .setMessage("Telefon sadece Wi‑Fi ile internete bağlanıyorsa, kapattıktan sonra bir daha komut alamaz; Wi‑Fi'yi elle açmak gerekir.")
                                .setNegativeButton("Vazgeç", null)
                                .setPositiveButton("Yine de kapat", (dd, ww) -> send("WIFI_OFF", "0", "Wi‑Fi kapat (süresiz)"))
                                .show();
                    } else {
                        send("WIFI_OFF", String.valueOf(min), "Wi‑Fi kapat (" + Exec.dur(min) + ")");
                    }
                })
                .show();
    }

    private static int minutesUntil(int hour, int minute) {
        Calendar now = Calendar.getInstance();
        Calendar t = (Calendar) now.clone();
        t.set(Calendar.HOUR_OF_DAY, hour);
        t.set(Calendar.MINUTE, minute);
        t.set(Calendar.SECOND, 0);
        if (!t.after(now)) t.add(Calendar.DAY_OF_MONTH, 1);
        return (int) Math.max(1, (t.getTimeInMillis() - now.getTimeInMillis()) / 60000L);
    }

    private void askNight() {
        int s0 = P.num(this, "night_s", 23 * 60);
        int e0 = P.num(this, "night_e", 7 * 60);
        Toast.makeText(this, "Önce BAŞLANGIÇ saatini seç", Toast.LENGTH_SHORT).show();
        new TimePickerDialog(this, (v1, sh, sm) -> {
            Toast.makeText(this, "Şimdi BİTİŞ saatini seç", Toast.LENGTH_SHORT).show();
            new TimePickerDialog(this, (v2, eh, em) -> {
                int s = sh * 60 + sm, e = eh * 60 + em;
                P.put(this, "night_s", s);
                P.put(this, "night_e", e);
                send("NIGHT", s + "-" + e, "Gece engeli " + Blocker.hhmm(s) + "–" + Blocker.hhmm(e));
            }, e0 / 60, e0 % 60, true).show();
        }, s0 / 60, s0 % 60, true).show();
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("Rolü sıfırla?")
                .setMessage("Eklediğin telefonlar silinmez; sadece bu telefonun rolü yeniden sorulur.")
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Sıfırla", (d, w) -> {
                    P.sp(this).edit().remove("role").apply();
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                })
                .show();
    }

    // ---------- Gönderme ----------

    private void send(String cmd, String arg, String label) {
        List<Device> t = targets();
        if (t.isEmpty()) {
            Toast.makeText(this, "Önce bir telefon ekle", Toast.LENGTH_SHORT).show();
            return;
        }
        for (Device d : t) sendTo(d, cmd, arg, label);
    }

    private void sendTo(final Device d, final String cmd, final String arg, final String label) {
        addLog("→ " + d.name + ": " + label);
        new Thread(() -> {
            boolean ok = Proto.post(Proto.topic(d.code), Proto.pack(d.code, cmd, arg));
            h.post(() -> {
                if (!ok) {
                    addLog("✗ " + d.name + ": gönderilemedi (A35'te internet var mı?)");
                    Toast.makeText(this, "Gönderilemedi — internet yok mu?", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Gönderildi → " + d.name, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    // ---------- Cevapları dinleme ----------

    @Override
    protected void onResume() {
        super.onResume();
        startStream();
    }

    @Override
    protected void onPause() {
        stopStream();
        super.onPause();
    }

    private void restartStream() {
        stopStream();
        startStream();
    }

    private void startStream() {
        if (devices.isEmpty()) return;
        final Map<String, Device> byTopic = new HashMap<>();
        StringBuilder topics = new StringBuilder();
        for (Device d : devices) {
            String rt = Proto.replyTopic(d.code);
            byTopic.put(rt, d);
            if (topics.length() > 0) topics.append(',');
            topics.append(rt);
        }
        final String topicList = topics.toString();
        streaming = true;
        streamThread = new Thread(() -> {
            int backoff = 2;
            while (streaming) {
                HttpURLConnection c = null;
                try {
                    long now = System.currentTimeMillis() / 1000L;
                    long since = Math.max(P.lng(this, "reply_time"), now - 10 * 60);
                    c = (HttpURLConnection) new URL(Proto.SERVER + topicList + "/json?since=" + since).openConnection();
                    streamConn = c;
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(100000);
                    BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                    backoff = 2;
                    String line;
                    while (streaming && (line = br.readLine()) != null) {
                        handleReply(byTopic, line);
                    }
                } catch (Exception ignored) {
                } finally {
                    if (c != null) {
                        try {
                            c.disconnect();
                        } catch (Exception ignored) {
                        }
                    }
                    streamConn = null;
                }
                if (!streaming) break;
                try {
                    Thread.sleep(backoff * 1000L);
                } catch (InterruptedException e) {
                    break;
                }
                backoff = Math.min(backoff * 2, 30);
            }
        }, "kumanda-cevap");
        streamThread.start();
    }

    private void stopStream() {
        streaming = false;
        final HttpURLConnection c = streamConn;
        if (c != null) {
            new Thread(() -> {
                try {
                    c.disconnect();
                } catch (Exception ignored) {
                }
            }).start();
        }
        if (streamThread != null) streamThread.interrupt();
        streamThread = null;
    }

    private void handleReply(Map<String, Device> byTopic, String line) {
        try {
            JSONObject o = new JSONObject(line);
            if (!"message".equals(o.optString("event"))) return;
            if (!P.markSeen(this, "reply_seen", o.optString("id"))) return;
            long t = o.optLong("time", 0);
            if (t > P.lng(this, "reply_time")) P.put(this, "reply_time", t);
            Device d = byTopic.get(o.optString("topic"));
            if (d == null) return;
            String[] cm = Proto.unpack(d.code, o.optString("message"), 24 * 3600);
            if (cm == null || !"ACK".equals(cm[0])) return;
            final String msg = "← " + d.name + ": " + cm[1];
            h.post(() -> addLog(msg));
        } catch (Exception ignored) {
        }
    }

    // ---------- Kayıt ----------

    private void addLog(String s) {
        try {
            JSONArray a = new JSONArray(P.str(this, "log", "[]"));
            String time = new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date());
            JSONArray n = new JSONArray();
            n.put(time + "  " + s);
            for (int i = 0; i < a.length() && i < 29; i++) n.put(a.getString(i));
            P.put(this, "log", n.toString());
        } catch (Exception ignored) {
        }
        renderLog();
    }

    private void renderLog() {
        if (logView == null) return;
        try {
            JSONArray a = new JSONArray(P.str(this, "log", "[]"));
            if (a.length() == 0) {
                logView.setText("Henüz bir şey yok. Bir komut gönderince cevap burada görünür.");
                logView.setTextColor(Ui.MUTED);
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < a.length() && i < 12; i++) {
                if (i > 0) sb.append('\n');
                sb.append(a.getString(i));
            }
            logView.setText(sb.toString());
            logView.setTextColor(Ui.TEXT);
        } catch (Exception ignored) {
        }
    }

}
