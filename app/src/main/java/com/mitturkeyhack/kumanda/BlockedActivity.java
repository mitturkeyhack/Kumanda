package com.mitturkeyhack.kumanda;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;

/**
 * Sistem penceresi görünümünde uyarı.
 * - Engellenen uygulama açılınca: "X şu an çalışmıyor"
 * - Kumanda'dan "şarj az" komutu gelince: "Pil seviyesi düşük"
 */
public class BlockedActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String title = getIntent().getStringExtra("title");
        String msg = getIntent().getStringExtra("msg");
        String btn = "Tamam";
        if (title == null || msg == null) {
            String app = getIntent().getStringExtra("app");
            if (app == null) app = "Uygulama";
            title = app;
            msg = app + " şu an çalışmıyor.\nLütfen daha sonra tekrar deneyin.";
        } else {
            Screen.wake(this);
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(btn, null)
                .setOnDismissListener(d -> finish())
                .show();
    }
}
