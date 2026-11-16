package com.mitturkeyhack.kumanda;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.util.List;

/** Kumanda'dan komut gönderme. */
public final class Sender {

    private static final Handler H = new Handler(Looper.getMainLooper());

    private Sender() {}

    public static void send(final Context ctx, final Devices.Device d, final String cmd, final String arg,
                            final String label, final boolean toast) {
        final Context c = ctx.getApplicationContext();
        KLog.add(c, "→ " + d.name + ": " + label);
        new Thread(() -> {
            boolean ok = Proto.post(Proto.topic(d.code), Proto.pack(d.code, cmd, arg));
            if (!ok) KLog.add(c, "✗ " + d.name + ": gönderilemedi (internet var mı?)");
            if (toast) {
                H.post(() -> Toast.makeText(c, ok ? "Gönderildi → " + d.name : "Gönderilemedi — internet yok mu?",
                        Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    /** Tüm telefonlara gönderir (widget ve kısayollar için). false = hiç telefon yok. */
    public static boolean sendAll(Context ctx, String cmd, String arg, String label) {
        List<Devices.Device> list = Devices.load(ctx);
        if (list.isEmpty()) {
            Toast.makeText(ctx, "Kumanda'da henüz telefon ekli değil", Toast.LENGTH_SHORT).show();
            return false;
        }
        for (Devices.Device d : list) send(ctx, d, cmd, arg, label, false);
        Toast.makeText(ctx, label + " → " + (list.size() == 1 ? list.get(0).name : "tüm telefonlar"),
                Toast.LENGTH_SHORT).show();
        return true;
    }
}
