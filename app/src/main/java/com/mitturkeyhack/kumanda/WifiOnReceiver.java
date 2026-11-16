package com.mitturkeyhack.kumanda;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;

/** Süreli Wi‑Fi kapatmanın süresi dolunca Wi‑Fi'yi geri açar. */
public class WifiOnReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        WifiManager w = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (w != null) w.setWifiEnabled(true);
    }
}
