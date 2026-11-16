package com.mitturkeyhack.kumanda;

import android.app.Activity;
import android.os.Bundle;

/** Uygulama simgesine uzun basınca çıkan kısayollar. */
public class ShortcutActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String a = getIntent().getAction();
        if (a != null && a.startsWith(KumandaWidget.PFX)) {
            KumandaWidget.run(this, a.substring(KumandaWidget.PFX.length()));
        }
        finish();
    }
}
