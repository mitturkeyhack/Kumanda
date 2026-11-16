package com.mitturkeyhack.kumanda;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Telefonlar arası haberleşme.
 * Komutlar ücretsiz ntfy.sh sunucusu üzerinden gider. Her alıcı telefonun gizli bir
 * eşleşme kodu var; kanal adı bu koddan türetilir ve her mesaj bu kodla imzalanır.
 * Kodu bilmeyen biri komut gönderemez.
 */
public final class Proto {

    public static final String SERVER = "https://ntfy.sh/";
    private static final String ALPH = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private Proto() {}

    public static String newCode() {
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) sb.append('-');
            sb.append(ALPH.charAt(r.nextInt(ALPH.length())));
        }
        return sb.toString();
    }

    /** Kullanıcının yazdığı kodu sadeleştirir (boşluk, tire, küçük harf, O/0 ve I/1 karışıklığı). */
    public static String norm(String code) {
        if (code == null) return "";
        String up = code.toUpperCase(java.util.Locale.ROOT)
                .replace('O', '0').replace('I', '1');
        StringBuilder sb = new StringBuilder();
        for (char c : up.toCharArray()) {
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) sb.append(c);
        }
        // Kod alfabesinde 0 ve 1 yok; geri çevir ki eşleşsin
        return sb.toString().replace('0', 'O').replace('1', 'I');
    }

    public static boolean validCode(String code) {
        return norm(code).length() == 12;
    }

    public static String topic(String code) {
        return "kmd" + sha256("kumanda-v1:" + norm(code)).substring(0, 28);
    }

    public static String replyTopic(String code) {
        return topic(code) + "r";
    }

    /** Mesaj biçimi: v1|zaman|KOMUT|arguman|imza */
    public static String pack(String code, String cmd, String arg) {
        long ts = System.currentTimeMillis() / 1000L;
        String safeArg = arg == null ? "" : arg.replace('|', '/');
        String data = "v1|" + ts + "|" + cmd + "|" + safeArg;
        return data + "|" + hmac(norm(code), data).substring(0, 32);
    }

    /** İmza ve zaman doğruysa {komut, argüman} döner, değilse null. */
    public static String[] unpack(String code, String msg, long maxAgeSec) {
        if (msg == null) return null;
        String[] p = msg.split("\\|", -1);
        if (p.length != 5 || !"v1".equals(p[0])) return null;
        String data = p[0] + "|" + p[1] + "|" + p[2] + "|" + p[3];
        String sig = hmac(norm(code), data).substring(0, 32);
        if (!sig.equals(p[4])) return null;
        long ts;
        try {
            ts = Long.parseLong(p[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        long now = System.currentTimeMillis() / 1000L;
        if (Math.abs(now - ts) > maxAgeSec) return null;
        return new String[]{p[2], p[3], p[1]};
    }

    /** ntfy kanalına mesaj gönderir. Ağ işlemi: ana iş parçacığında çağırma. */
    public static boolean post(String topic, String body) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(SERVER + topic).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(15000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(b.length);
            OutputStream os = c.getOutputStream();
            os.write(b);
            os.close();
            int code = c.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return hex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String hmac(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return hex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
