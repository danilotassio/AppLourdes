package br.lourdinha;
import android.content.Context;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;
/** Avisos pela internet (servidor Cloudflare + Firebase Cloud Messaging). */
public class Push {
    static final String BASE = "https://lourdinha.danilotassioestudos.workers.dev";
    public static boolean valid(String c){ return c != null && c.matches("[A-Za-z0-9_-]{20,64}"); }
    public static String code(Context c){ return LogStore.sp(c).getString("pair", ""); }
    public static String mode(Context c){ return LogStore.sp(c).getString("mode", ""); }
    public static String newCode(){
        String a = "abcdefghijkmnpqrstuvwxyz23456789"; java.security.SecureRandom r = new java.security.SecureRandom();
        StringBuilder s = new StringBuilder("L"); for (int i = 0; i < 28; i++) s.append(a.charAt(r.nextInt(a.length())));
        return s.toString();
    }
    static void post(String path, JSONObject o){
        final String body = o.toString();
        new Thread(() -> {
            for (int tent = 0; tent < 2; tent++) {
                try {
                    HttpURLConnection h = (HttpURLConnection) new URL(BASE + path).openConnection();
                    h.setRequestMethod("POST"); h.setConnectTimeout(8000); h.setReadTimeout(8000); h.setDoOutput(true);
                    h.setRequestProperty("content-type", "application/json");
                    try (OutputStream os = h.getOutputStream()) { os.write(body.getBytes("UTF-8")); }
                    int rc = h.getResponseCode(); h.disconnect();
                    if (rc < 500) return;
                } catch (Throwable e) {}
                try { Thread.sleep(4000); } catch (Throwable e) {}
            }
        }).start();
    }
    /** Envia um aviso para os cuidadores pareados (nao faz nada se nao houver codigo). */
    public static void send(Context c, String msg){
        try {
            if (!"lourdinha".equals(mode(c))) return;
            String code = code(c); if (!valid(code) || msg == null || msg.isEmpty()) return;
            String body = msg.startsWith("AppLourdinha: ") ? msg.substring(14) : msg;
            post("/send", new JSONObject().put("c", code).put("title", "AppLourdinha").put("body", body).put("kind", "aviso"));
        } catch (Throwable e) {}
    }
    /** Cuidador: registra este aparelho no codigo informado. */
    public static void register(Context c){
        try {
            final String code = code(c); if (!valid(code) || !"cuidador".equals(mode(c))) return;
            com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken().addOnSuccessListener(t -> {
                try { post("/reg", new JSONObject().put("c", code).put("t", t)); } catch (Throwable e) {}
            });
        } catch (Throwable e) {}
    }
    /** Lourdinha: publica o resumo dos dados para o cuidador. */
    public static void state(Context c, String json){
        try {
            if (!"lourdinha".equals(mode(c))) return;
            String code = code(c); if (!valid(code) || json == null || json.length() > 150000) return;
            post("/state", new JSONObject().put("c", code).put("d", json));
        } catch (Throwable e) {}
    }
    /** Cuidador: busca o resumo e guarda em "state"; depois chama o retorno na thread atual. */
    public static void fetch(Context c, Runnable done){
        final String code = code(c);
        if (!valid(code)) return;
        new Thread(() -> {
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(BASE + "/get").openConnection();
                h.setRequestMethod("POST"); h.setConnectTimeout(8000); h.setReadTimeout(8000); h.setDoOutput(true);
                h.setRequestProperty("content-type", "application/json");
                try (OutputStream os = h.getOutputStream()) { os.write(new JSONObject().put("c", code).toString().getBytes("UTF-8")); }
                java.io.InputStream in = h.getResponseCode() < 400 ? h.getInputStream() : null;
                if (in != null) {
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(); byte[] b = new byte[4096]; int n;
                    while ((n = in.read(b)) > 0) bo.write(b, 0, n);
                    String d = new JSONObject(bo.toString("UTF-8")).optString("d", "");
                    if (!d.isEmpty()) { LogStore.sp(c).edit().putString("state", d).apply(); if (done != null) done.run(); }
                }
                h.disconnect();
            } catch (Throwable e) {}
        }).start();
    }
    /** Cuidador: publica as definicoes secretas (ex.: localizacao). */
    public static void cfgSet(Context c, String json){
        try { String code = code(c); if (!valid(code) || json == null || json.length() > 2000) return; post("/cfgset", new JSONObject().put("c", code).put("d", json)); } catch (Throwable e) {}
    }
    /** Lourdinha: busca as definicoes secretas e guarda em "cfg". */
    public static void cfgFetch(Context c, Runnable done){
        final String code = code(c);
        if (!valid(code) || !"lourdinha".equals(mode(c))) return;
        new Thread(() -> {
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(BASE + "/cfgget").openConnection();
                h.setRequestMethod("POST"); h.setConnectTimeout(8000); h.setReadTimeout(8000); h.setDoOutput(true);
                h.setRequestProperty("content-type", "application/json");
                try (OutputStream os = h.getOutputStream()) { os.write(new JSONObject().put("c", code).toString().getBytes("UTF-8")); }
                if (h.getResponseCode() < 400) {
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(); byte[] b = new byte[2048]; int n;
                    java.io.InputStream in = h.getInputStream();
                    while ((n = in.read(b)) > 0) bo.write(b, 0, n);
                    String d = new JSONObject(bo.toString("UTF-8")).optString("d", "");
                    if (!d.isEmpty()) { new JSONObject(d); LogStore.sp(c).edit().putString("cfg", d).apply(); if (done != null) done.run(); }
                }
                h.disconnect();
            } catch (Throwable e) {}
        }).start();
    }
}
