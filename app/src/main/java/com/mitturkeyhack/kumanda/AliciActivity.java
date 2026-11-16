package com.mitturkeyhack.kumanda;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** Alıcı telefonun ekranı: eşleşme kodu + kurulum adımları + bağlantı durumu. */
public class AliciActivity extends Activity {

    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView connText;
    private TextView stAcc, stAdmin, stBattery, stUsage, stOverlay;
    private Button btAcc, btAdmin, btBattery, btUsage, btOverlay;

    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            refresh();
            h.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        root.setPadding(p, Ui.dp(this, 24), p, p);
        sv.addView(root);

        root.addView(Ui.text(this, "Alıcı telefon", 26, Ui.TEXT, true));
        TextView sub = Ui.text(this, "Bu telefon Kumanda ile uzaktan yönetiliyor.", 14, Ui.MUTED, false);
        sub.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 16));
        root.addView(sub);

        // Eşleşme kodu
        LinearLayout codeCard = Ui.card(this);
        codeCard.addView(Ui.section(this, "Eşleşme kodu"));
        TextView code = Ui.text(this, P.myCode(this), 30, Ui.BLUE, true);
        code.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        code.setGravity(Gravity.CENTER);
        code.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        code.setTextIsSelectable(true);
        codeCard.addView(code);
        TextView hint = Ui.text(this, "Bu kodu A35'teki Kumanda uygulamasında \"＋ Telefon ekle\" ile gir.", 13, Ui.MUTED, false);
        hint.setGravity(Gravity.CENTER);
        codeCard.addView(hint);
        root.addView(codeCard);

        // Bağlantı
        LinearLayout connCard = Ui.card(this);
        connCard.addView(Ui.section(this, "Bağlantı"));
        connText = Ui.text(this, "…", 16, Ui.TEXT, true);
        connCard.addView(connText);
        root.addView(connCard);

        // Kurulum
        LinearLayout setup = Ui.card(this);
        setup.addView(Ui.section(this, "Kurulum (bir kere yapılır)"));

        stAcc = status();
        btAcc = Ui.btn(this, "Aç", Ui.BLUE);
        btAcc.setOnClickListener(v -> openAccessibility());
        setup.addView(step("1. Uygulama engeli (TikTok / YouTube)",
                "Erişilebilirlik ayarlarında \"Kumanda uygulama engeli\"ni AÇ.", stAcc, btAcc));

        stUsage = status();
        btUsage = Ui.btn(this, "Aç", Ui.BLUE);
        btUsage.setOnClickListener(v -> openSimple(Settings.ACTION_USAGE_ACCESS_SETTINGS,
                "Listede Kumanda'yı bul ve izin ver"));
        setup.addView(step("1b. Yedek engel: Kullanım erişimi",
                "Erişilebilirlik takılırsa engel bununla çalışır. \"Kullanım erişimi\"nde Kumanda'yı AÇ.", stUsage, btUsage));

        stOverlay = status();
        btOverlay = Ui.btn(this, "Aç", Ui.BLUE);
        btOverlay.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                openAppDetails();
            }
        });
        setup.addView(step("1c. Diğer uygulamaların üstünde göster",
                "\"Şu an çalışmıyor\" uyarısının çıkabilmesi için AÇ.", stOverlay, btOverlay));

        stAdmin = status();
        btAdmin = Ui.btn(this, "Aç", Ui.BLUE);
        btAdmin.setOnClickListener(v -> openAdmin());
        setup.addView(step("2. Ekran kilitleme izni",
                "Çıkan ekranda \"Etkinleştir\"e dokun.", stAdmin, btAdmin));

        stBattery = status();
        btBattery = Ui.btn(this, "Aç", Ui.BLUE);
        btBattery.setOnClickListener(v -> openBattery());
        setup.addView(step("3. Pil kısıtlamasını kaldır",
                "Uygulama arka planda kapanmasın diye \"İzin ver\" de.", stBattery, btBattery));

        Button btAuto = Ui.btn(this, "Ayarlar", Ui.SLATE);
        btAuto.setOnClickListener(v -> openAutostart());
        setup.addView(step("4. Otomatik başlatma",
                "Redmi: Kumanda için \"Otomatik başlat\"ı aç.\nHuawei: Uygulama başlatma › Kumanda › \"Manuel yönet\" seç, üç anahtarı da aç.",
                null, btAuto));

        Button btPerm = Ui.btn(this, "İzinler", Ui.SLATE);
        btPerm.setOnClickListener(v -> openAppDetails());
        setup.addView(step("5. Redmi izinleri",
                "İzinler › Diğer izinler: \"Arka planda çalışırken açılır pencere\", \"Kilit ekranında göster\", \"Wi‑Fi'yi değiştir\", \"Bluetooth'u aç/kapat\" → hepsine İzin ver.",
                null, btPerm));

        root.addView(setup);

        Button reset = Ui.flatBtn(this, "Rolü sıfırla", Ui.MUTED);
        reset.setOnClickListener(v -> confirmReset());
        root.addView(reset);

        setContentView(sv);
    }

    private TextView status() {
        TextView t = Ui.text(this, "", 14, Ui.MUTED, true);
        return t;
    }

    private LinearLayout step(String title, String desc, TextView st, Button bt) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(this, title, 15, Ui.TEXT, true));
        TextView d = Ui.text(this, desc, 13, Ui.MUTED, false);
        d.setPadding(0, Ui.dp(this, 2), 0, 0);
        col.addView(d);
        if (st != null) {
            st.setPadding(0, Ui.dp(this, 4), 0, 0);
            col.addView(st);
        }
        row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(this, 92), LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.setMargins(Ui.dp(this, 10), 0, 0, 0);
        row.addView(bt, blp);
        return row;
    }

    @Override
    protected void onResume() {
        super.onResume();
        ListenerService.start(this);
        h.post(refresher);
    }

    @Override
    protected void onPause() {
        h.removeCallbacks(refresher);
        super.onPause();
    }

    private void refresh() {
        String s = P.str(this, "conn_status", "Bağlanıyor…");
        connText.setText(s);
        connText.setTextColor(s.startsWith("Bağlı") ? Ui.GREEN : Ui.ORANGE);

        boolean accSet = accessibilityOn();
        boolean accRun = BlockService.isRunning();
        if (accSet && !accRun) {
            stAcc.setText("⚠ Açık ama ÇALIŞMIYOR — ayarda kapatıp tekrar aç");
            stAcc.setTextColor(Ui.ORANGE);
            btAcc.setAlpha(1f);
        } else {
            mark(stAcc, btAcc, accRun);
        }
        mark(stUsage, btUsage, Guard.usageOk(this));
        mark(stOverlay, btOverlay, Guard.overlayOk(this));
        mark(stAdmin, btAdmin, adminOn());
        mark(stBattery, btBattery, batteryOk());
    }

    private void mark(TextView st, Button bt, boolean ok) {
        st.setText(ok ? "✓ Tamam" : "✗ Yapılmadı");
        st.setTextColor(ok ? Ui.GREEN : Ui.RED);
        bt.setAlpha(ok ? 0.45f : 1f);
    }

    private void openSimple(String action, String toast) {
        try {
            startActivity(new Intent(action));
            Toast.makeText(this, toast, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            openAppDetails();
        }
    }

    private boolean accessibilityOn() {
        String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.toLowerCase(Locale.ROOT).contains(getPackageName().toLowerCase(Locale.ROOT) + "/");
    }

    private ComponentName admin() {
        return new ComponentName(this, AdminReceiver.class);
    }

    private boolean adminOn() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        return dpm.isAdminActive(admin());
    }

    private boolean batteryOk() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void openAccessibility() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, "Listede \"Kumanda uygulama engeli\"ni bul ve aç", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Ayarlar açılamadı", Toast.LENGTH_SHORT).show();
        }
    }

    private void openAdmin() {
        Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin());
        i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Kumanda'dan \"Ekranı kilitle\" komutu gelince ekranı kilitleyebilmek için.");
        try {
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Açılamadı", Toast.LENGTH_SHORT).show();
        }
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
                openAppDetails();
            }
        }
    }

    private void openAutostart() {
        String brand = Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        String[][] targets;
        if (brand.contains("xiaomi") || brand.contains("redmi")) {
            targets = new String[][]{
                    {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
            };
        } else if (brand.contains("huawei") || brand.contains("honor")) {
            targets = new String[][]{
                    {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                    {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"},
                    {"com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"},
            };
        } else {
            targets = new String[0][];
        }
        for (String[] t : targets) {
            try {
                Intent i = new Intent();
                i.setComponent(new ComponentName(t[0], t[1]));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            } catch (Exception ignored) {
            }
        }
        openAppDetails();
    }

    private void openAppDetails() {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("Rolü sıfırla?")
                .setMessage("Bu telefon artık komut almaz. Tekrar Alıcı seçersen YENİ bir kod oluşur ve Kumanda'ya yeniden eklemen gerekir.")
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Sıfırla", (d, w) -> {
                    P.sp(this).edit().remove("role").remove("my_code").remove("last_time").remove("seen_ids")
                            .putBoolean(Blocker.K_TIKTOK, false).putBoolean(Blocker.K_YOUTUBE, false)
                            .putBoolean(Blocker.K_NIGHT_ON, false).putBoolean(Blocker.K_CAP_ON, false)
                            .remove(Blocker.K_CUSTOM).remove(Blocker.K_LIMIT_TT).remove(Blocker.K_LIMIT_YT).apply();
                    ListenerService.stop(this);
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                })
                .show();
    }
}
