package com.mitturkeyhack.kumanda;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;

/** Engellenen uygulama açılınca çıkan "şu an çalışmıyor" uyarısı. */
public class BlockedActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String app = getIntent().getStringExtra("app");
        if (app == null) app = "Uygulama";
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle(app)
                .setMessage(app + " şu an çalışmıyor.\nLütfen daha sonra tekrar deneyin.")
                .setPositiveButton("Tamam", null)
                .setOnDismissListener(d -> finish())
                .show();
    }
}
