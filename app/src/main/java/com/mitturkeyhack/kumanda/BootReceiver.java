package com.mitturkeyhack.kumanda;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Telefon açılınca / uygulama güncellenince dinlemeyi yeniden başlatır. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String role = P.role(c);
        if (P.ROLE_ALICI.equals(role)) ListenerService.start(c);
        else if (P.ROLE_KUMANDA.equals(role)) KumandaService.start(c);
    }
}
