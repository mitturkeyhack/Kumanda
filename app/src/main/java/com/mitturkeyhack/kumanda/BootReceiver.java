package com.mitturkeyhack.kumanda;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Telefon açılınca / uygulama güncellenince dinlemeyi yeniden başlatır. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (P.ROLE_ALICI.equals(P.role(c))) ListenerService.start(c);
    }
}
