package com.mitturkeyhack.kumanda;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** A35'teki kumanda ekranı. */
public class KumandaActivity extends Activity {

    private final Handler h = new Handler(Looper.getMainLooper());
    private List<Devices.Device> devices = new ArrayList<>();
    private int selected = 0; // -1 = tümü

    private LinearLayout chips;
    private TextView logView;
    private TextView targetLabel;
    private Button batteryBtn;

    private ProgressDialog appsWait;
    private Devices.Device appsFor;

    private final Runnable logListener = this::renderLog;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        devices = Devices.load(this);
        selected = P.num(this, "selected", 0);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        KumandaService.start(this);
        KLog.listener = logListener;
        KumandaService.appsListener = this::onApps;
        renderLog();
        if (batteryBtn != null) batteryBtn.setVisibility(batteryOk() ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onPause() {
        if (KLog.listener == logListener) KLog.listener = null;
        KumandaService.appsListener = null;
        super.onPause();
    }

    private List<Devices.Device> targets() {
        List<Devices.Device> t = new ArrayList<>();
        if (devices.isEmpty()) return t;
        if (selected < 0) return new ArrayList<>(devices);
        if (selected >= devices.size()) selected = 0;
        t.add(devices.get(selected));
        return t;
    }

    private Devices.Device single() {
        if (devices.isEmpty()) {
            Toast.makeText(this, "Önce bir telefon ekle", Toast.LENGTH_SHORT).show();
            return null;
        }
        if (selected < 0) {
            Toast.makeText(this, "Bunun için yukarıdan tek bir telefon seç", Toast.LENGTH_LONG).show();
            return null;
        }
        return targets().get(0);
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

        // Hızlı
        LinearLayout c0 = Ui.card(this);
        c0.addView(Ui.section(this, "⚡  Hızlı"));
        c0.addView(Ui.row(this,
                cmdBtn("⛔ TikTok + YouTube engelle", Ui.RED, "BLOCK", "AL1"),
                cmdBtn("✅ İkisini de serbest bırak", Ui.GREEN, "BLOCK", "AL0")));
        root.addView(c0);

        // Ses
        LinearLayout c1 = Ui.card(this);
        c1.addView(Ui.section(this, "🔊  Ses (medya)"));
        c1.addView(Ui.row(this,
                cmdBtn("🔇 Sesi kapat", Ui.RED, "VOL", "0"),
                cmdBtn("Kıs (%30)", Ui.SLATE, "VOL", "30")));
        c1.addView(Ui.row(this,
                cmdBtn("Orta (%60)", Ui.SLATE, "VOL", "60"),
                cmdBtn("Sonuna kadar", Ui.BLUE, "VOL", "100")));
        Button cap = Ui.btn(this, "🔉 Ses tavanı…", Ui.PURPLE);
        cap.setOnClickListener(v -> askCap());
        c1.addView(Ui.row(this, cap, cmdBtn("Tavanı kaldır", Ui.SLATE, "CAP", "OFF")));
        c1.addView(hint("Ses tavanı: sesi açmaya çalışsalar bile seçtiğin seviyenin üstüne çıkmaz."));
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
        Button night = Ui.btn(this, "🌙 Gece engeli…", Ui.PURPLE);
        night.setOnClickListener(v -> askNight());
        c2.addView(Ui.row(this, night, cmdBtn("Gece engeli kapat", Ui.SLATE, "NIGHT", "OFF")));
        Button limit = Ui.btn(this, "⏱️ Günlük süre…", Ui.ORANGE);
        limit.setOnClickListener(v -> askLimit());
        Button other = Ui.btn(this, "📋 Diğer uygulamalar…", Ui.BLUE);
        other.setOnClickListener(v -> requestApps());
        c2.addView(Ui.row(this, limit, other));
        c2.addView(hint("Gece engeli: her gün seçtiğin saatlerde TikTok ve YouTube kapanır.\n"
                + "Günlük süre: ör. TikTok günde 1 saat; dolunca o gün açılmaz.\n"
                + "Diğer uygulamalar: telefondaki listeden istediğini (Instagram, oyun…) seç."));
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

        // Telefon
        LinearLayout c4 = Ui.card(this);
        c4.addView(Ui.section(this, "📱  Telefon"));
        Button msg = Ui.btn(this, "💬 Mesaj gönder…", Ui.BLUE);
        msg.setOnClickListener(v -> askMessage());
        Button ring = Ui.btn(this, "🔔 Telefonu çaldır", Ui.PINK);
        ring.setOnClickListener(v -> send("RING", "30", "Telefonu çaldır (30 sn)"));
        c4.addView(Ui.row(this, msg, ring));
        c4.addView(Ui.row(this,
                cmdBtn("🔒 Ekranı kilitle", Ui.ORANGE, "LOCK", ""),
                cmdBtn("ℹ️ Durumu sor", Ui.SLATE, "STATUS", "")));
        c4.addView(Ui.row(this, cmdBtn("Çalmayı durdur", Ui.SLATE, "RING_STOP", "")));
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

        // Bu telefon
        LinearLayout c6 = Ui.card(this);
        c6.addView(Ui.section(this, "⚙️  Bu telefon"));
        c6.addView(hint("Şarj uyarıları (%15 altı) uygulama kapalıyken de bildirim olarak gelir.\n"
                + "Ana ekran kısayolu: boş bir yere basılı tut › Widget'lar › Kumanda. "
                + "Ya da uygulama simgesine basılı tut."));
        batteryBtn = Ui.btn(this, "Bildirimler gecikmesin: pil kısıtlamasını kaldır", Ui.SLATE);
        batteryBtn.setOnClickListener(v -> openBattery());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.setMargins(0, Ui.dp(this, 10), 0, 0);
        c6.addView(batteryBtn, blp);
        root.addView(c6);

        Button reset = Ui.flatBtn(this, "Rolü sıfırla", Ui.MUTED);
        reset.setOnClickListener(v -> confirmReset());
        root.addView(reset);

        setContentView(sv);
        renderChips();
        renderLog();
    }

    private TextView hint(String s) {
        TextView t = Ui.text(this, s, 12, Ui.MUTED, false);
        t.setLineSpacing(0, 1.1f);
        return t;
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
        add.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        add.setOnClickListener(v -> askAdd());
        chips.addView(add, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

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

    // ---------- Telefon ekle / sil ----------

    private void askAdd() {
        LinearLayout l = dialogBox();
        final EditText name = new EditText(this);
        name.setHint("Ad (ör. Annem)");
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        l.addView(name);
        final EditText code = new EditText(this);
        code.setHint("Kod (ör. ABCD-EFGH-JKLM)");
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        l.addView(code);
        l.addView(hint("Kod, diğer telefonda Kumanda › Alıcı ekranında yazıyor."));

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
            devices.add(new Devices.Device(n, pretty));
            Devices.save(this, devices);
            selected = devices.size() - 1;
            P.put(this, "selected", selected);
            renderChips();
            KumandaService.reload(this);
            d.dismiss();
            final Devices.Device dev = devices.get(selected);
            h.postDelayed(() -> Sender.send(this, dev, "PING", "", "Bağlantı testi", true), 1500);
        }));
        d.show();
    }

    private void confirmDelete(final int idx) {
        new AlertDialog.Builder(this)
                .setTitle(devices.get(idx).name + " silinsin mi?")
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Sil", (d, w) -> {
                    devices.remove(idx);
                    Devices.save(this, devices);
                    selected = 0;
                    P.put(this, "selected", 0);
                    renderChips();
                    KumandaService.reload(this);
                })
                .show();
    }

    private LinearLayout dialogBox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 22);
        l.setPadding(p, Ui.dp(this, 8), p, 0);
        return l;
    }

    // ---------- Diyaloglar ----------

    private void askWifiOff() {
        final String[] items = {"15 dakika", "1 saat", "Sabah 07:00'ye kadar", "Süresiz (dikkat!)"};
        new AlertDialog.Builder(this)
                .setTitle("Wi‑Fi ne kadar kapalı kalsın?")
                .setItems(items, (d, which) -> {
                    int min = which == 0 ? 15 : which == 1 ? 60 : which == 2 ? minutesUntil(7, 0) : 0;
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

    /** İki saat seçtirir (başlangıç, bitiş). */
    private interface RangeCb {
        void onRange(int s, int e);
    }

    private void pickRange(String what, int s0, int e0, RangeCb cb) {
        Toast.makeText(this, what + ": önce BAŞLANGIÇ saatini seç", Toast.LENGTH_SHORT).show();
        new TimePickerDialog(this, (v1, sh, sm) -> {
            Toast.makeText(this, "Şimdi BİTİŞ saatini seç", Toast.LENGTH_SHORT).show();
            new TimePickerDialog(this, (v2, eh, em) -> cb.onRange(sh * 60 + sm, eh * 60 + em),
                    e0 / 60, e0 % 60, true).show();
        }, s0 / 60, s0 % 60, true).show();
    }

    private void askNight() {
        pickRange("Gece engeli", P.num(this, "night_s", 23 * 60), P.num(this, "night_e", 7 * 60), (s, e) -> {
            P.put(this, "night_s", s);
            P.put(this, "night_e", e);
            send("NIGHT", s + "-" + e, "Gece engeli " + Blocker.hhmm(s) + "–" + Blocker.hhmm(e));
        });
    }

    private void askCap() {
        final int[] pcts = {20, 30, 40, 50, 60};
        String[] items = new String[pcts.length];
        for (int i = 0; i < pcts.length; i++) items[i] = "En fazla %" + pcts[i];
        new AlertDialog.Builder(this)
                .setTitle("Ses en fazla ne kadar olsun?")
                .setItems(items, (d, which) -> {
                    final int pct = pcts[which];
                    new AlertDialog.Builder(this)
                            .setTitle("Ne zaman geçerli olsun?")
                            .setItems(new String[]{"Her zaman", "Sadece belli saatlerde (ör. gece)"}, (d2, w2) -> {
                                if (w2 == 0) {
                                    send("CAP", pct + ",ALL", "Ses tavanı %" + pct + " (her zaman)");
                                } else {
                                    pickRange("Ses tavanı", P.num(this, "cap_s", 22 * 60), P.num(this, "cap_e", 7 * 60), (s, e) -> {
                                        P.put(this, "cap_s", s);
                                        P.put(this, "cap_e", e);
                                        send("CAP", pct + "," + s + "," + e,
                                                "Ses tavanı %" + pct + " (" + Blocker.hhmm(s) + "–" + Blocker.hhmm(e) + ")");
                                    });
                                }
                            })
                            .show();
                })
                .show();
    }

    private void askLimit() {
        new AlertDialog.Builder(this)
                .setTitle("Hangi uygulama?")
                .setItems(new String[]{"TikTok", "YouTube"}, (d, which) -> {
                    final String g = which == 0 ? "TT" : "YT";
                    final String name = which == 0 ? "TikTok" : "YouTube";
                    final int[] mins = {30, 60, 90, 120, 180, 0};
                    String[] items = {"30 dakika", "1 saat", "1,5 saat", "2 saat", "3 saat", "Sınırı kaldır"};
                    new AlertDialog.Builder(this)
                            .setTitle(name + " günde en fazla ne kadar?")
                            .setItems(items, (d2, w2) -> {
                                int m = mins[w2];
                                send("LIMIT", g + "," + m, m == 0 ? name + " süre sınırını kaldır"
                                        : name + " günde " + Exec.dur(m));
                            })
                            .show();
                })
                .show();
    }

    private void askMessage() {
        LinearLayout l = dialogBox();
        final EditText text = new EditText(this);
        text.setHint("Mesajını yaz…");
        text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        text.setMinLines(2);
        l.addView(text);

        String[] quick = {"Yemek hazır 🍲", "Hadi yatın artık 😊", "Beni arar mısınız? 📞", "Sesi biraz kısar mısınız? 🙏"};
        LinearLayout qrow = new LinearLayout(this);
        qrow.setOrientation(LinearLayout.VERTICAL);
        for (final String q : quick) {
            Button qb = Ui.flatBtn(this, q, Ui.BLUE);
            qb.setOnClickListener(v -> text.setText(q));
            qrow.addView(qb);
        }
        l.addView(qrow);

        final CheckBox speak = new CheckBox(this);
        speak.setText("Sesli de oku 🔊");
        speak.setChecked(true);
        l.addView(speak);

        final AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Ekrana mesaj gönder")
                .setView(l)
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Gönder", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String t = text.getText().toString().trim();
            if (t.isEmpty()) {
                text.setError("Bir şey yaz");
                return;
            }
            String shortT = t.length() > 30 ? t.substring(0, 30) + "…" : t;
            send("MSG", (speak.isChecked() ? "1" : "0") + "\n" + t, "Mesaj: " + shortT);
            d.dismiss();
        }));
        d.show();
    }

    // ---------- Diğer uygulamalar ----------

    private void requestApps() {
        final Devices.Device d = single();
        if (d == null) return;
        appsFor = d;
        appsWait = new ProgressDialog(this);
        appsWait.setMessage(d.name + " telefonundaki uygulamalar isteniyor…\n(İnternet yavaşsa biraz sürebilir)");
        appsWait.setCancelable(true);
        appsWait.setOnCancelListener(x -> appsFor = null);
        appsWait.show();
        Sender.send(this, d, "APPS", "", "Uygulama listesi iste", false);
        h.postDelayed(() -> {
            if (appsFor == d && appsWait != null && appsWait.isShowing()) {
                appsWait.dismiss();
                appsFor = null;
                Toast.makeText(this, "Cevap gelmedi. O telefonda internet ve Kumanda açık mı?", Toast.LENGTH_LONG).show();
            }
        }, 45_000);
    }

    private void onApps(Devices.Device d, List<String[]> apps) {
        if (appsFor == null || !appsFor.code.equals(d.code)) return;
        appsFor = null;
        if (appsWait != null && appsWait.isShowing()) appsWait.dismiss();
        if (isFinishing()) return;

        final int n = apps.size();
        final String[] labels = new String[n];
        final String[] pkgs = new String[n];
        final boolean[] checked = new boolean[n];
        for (int i = 0; i < n; i++) {
            String[] a = apps.get(i);
            checked[i] = "+".equals(a[0]);
            labels[i] = a[1];
            pkgs[i] = a[2];
        }
        new AlertDialog.Builder(this)
                .setTitle(d.name + ": engellenecekleri seç")
                .setMultiChoiceItems(labels, checked, (dd, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Kaydet", (dd, w) -> {
                    StringBuilder csv = new StringBuilder();
                    int cnt = 0;
                    for (int i = 0; i < n; i++) {
                        if (!checked[i]) continue;
                        if (csv.length() > 0) csv.append(',');
                        csv.append(pkgs[i]);
                        cnt++;
                    }
                    Sender.send(this, d, "BLOCKSET", csv.toString(),
                            cnt == 0 ? "Diğer uygulama engellerini kaldır" : cnt + " uygulamayı engelle", true);
                })
                .show();
    }

    // ---------- Diğer ----------

    private boolean batteryOk() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void openBattery() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("Rolü sıfırla?")
                .setMessage("Eklediğin telefonlar silinmez; sadece bu telefonun rolü yeniden sorulur.")
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Sıfırla", (d, w) -> {
                    P.sp(this).edit().remove("role").apply();
                    stopService(new Intent(this, KumandaService.class));
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                })
                .show();
    }

    private void send(String cmd, String arg, String label) {
        List<Devices.Device> t = targets();
        if (t.isEmpty()) {
            Toast.makeText(this, "Önce bir telefon ekle", Toast.LENGTH_SHORT).show();
            return;
        }
        for (Devices.Device d : t) Sender.send(this, d, cmd, arg, label, true);
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
            for (int i = 0; i < a.length() && i < 14; i++) {
                if (i > 0) sb.append('\n');
                sb.append(a.getString(i));
            }
            logView.setText(sb.toString());
            logView.setTextColor(Ui.TEXT);
        } catch (Exception ignored) {
        }
    }
}
