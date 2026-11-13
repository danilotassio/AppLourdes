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
                try { LogStore.sp(c).edit().putString("ftok", t).apply(); post("/reg", new JSONObject().put("c", code).put("t", t)); } catch (Throwable e) {}
            });
        } catch (Throwable e) {}
    }
    /** Lourdinha: publica o resumo dos dados para o cuidador. */
    public static void state(Context c, String json){
        try {
            if (!"lourdinha".equals(mode(c))) return;
            try { JSONObject cc = new JSONObject(json); cc.remove("ts"); LogStore.sp(c).edit().putString("stcache", cc.toString()).apply(); } catch (Throwable e) {}
            String code = code(c); if (!valid(code) || json == null || json.length() > 150000) return;
            post("/state", new JSONObject().put("c", code).put("d", json));
        } catch (Throwable e) {}
    }
    /** Cuidador: busca o resumo e guarda em "state"; depois chama o retorno na thread atual. */
    public static void fetch(Context c, Runnable done){
        final String code = code(c);
        if (!valid(code)) return;
        new Thread(() -> {
            String diag = "";
            try {
                HttpURLConnection g = (HttpURLConnection) new URL(BASE + "/").openConnection();
                g.setConnectTimeout(8000); g.setReadTimeout(8000);
                java.io.InputStream gi = g.getResponseCode() < 400 ? g.getInputStream() : null;
                if (gi != null) { java.io.ByteArrayOutputStream gb = new java.io.ByteArrayOutputStream(); byte[] gx = new byte[1024]; int gn; while ((gn = gi.read(gx)) > 0) gb.write(gx, 0, gn); diag = new JSONObject(gb.toString("UTF-8")).has("versao") ? "novo" : "antigo"; }
                else diag = "erro";
                g.disconnect();
            } catch (Throwable e) { diag = "offline"; }
            LogStore.sp(c).edit().putString("srv", diag).apply();
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
    /** Lourdinha: busca as definicoes secretas e guarda em "cfg" (bloqueia; usar fora da thread principal). */
    public static boolean cfgFetchNow(Context c){
        final String code = code(c);
        if (!valid(code) || !"lourdinha".equals(mode(c))) return false;
        try {
            HttpURLConnection h = (HttpURLConnection) new URL(BASE + "/cfgget").openConnection();
            h.setRequestMethod("POST"); h.setConnectTimeout(8000); h.setReadTimeout(8000); h.setDoOutput(true);
            h.setRequestProperty("content-type", "application/json");
            try (OutputStream os = h.getOutputStream()) { os.write(new JSONObject().put("c", code).toString().getBytes("UTF-8")); }
            boolean ok = false;
            if (h.getResponseCode() < 400) {
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(); byte[] b = new byte[2048]; int n;
                java.io.InputStream in = h.getInputStream();
                while ((n = in.read(b)) > 0) bo.write(b, 0, n);
                String d = new JSONObject(bo.toString("UTF-8")).optString("d", "");
                if (!d.isEmpty()) { new JSONObject(d); LogStore.sp(c).edit().putString("cfg", d).apply(); ok = true; }
            }
            h.disconnect();
            return ok;
        } catch (Throwable e) { return false; }
    }
    public static void cfgFetch(Context c, Runnable done){
        new Thread(() -> { if (cfgFetchNow(c) && done != null) done.run(); }).start();
    }
    /** Cuidador: avisa os demais cuidadores que ele esta ligando (so o primeiro dispara no servidor). */
    public static void care(Context c, String nome, String ref, String txt){
        try {
            final String code = code(c); if (!valid(code) || !"cuidador".equals(mode(c))) return;
            final JSONObject o = new JSONObject().put("c", code).put("n", nome).put("r", ref).put("x", txt);
            String t = LogStore.sp(c).getString("ftok", "");
            if (!t.isEmpty()) { post("/care", o.put("t", t)); return; }
            com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken().addOnSuccessListener(tk -> {
                try { LogStore.sp(c).edit().putString("ftok", tk).apply(); post("/care", o.put("t", tk)); } catch (Throwable e) {}
            });
        } catch (Throwable e) {}
    }

    /** Lourdinha: reenvia o ultimo resumo conhecido, com registro da semana e sintomas atualizados a partir do log nativo. */
    public static void stateRefresh(Context c){
        try {
            if (!"lourdinha".equals(mode(c)) || !valid(code(c))) return;
            String cache = LogStore.sp(c).getString("stcache", "");
            if (cache.isEmpty()) return;
            JSONObject o = new JSONObject(cache);
            long from = System.currentTimeMillis() - 7L * 86400000L;
            org.json.JSONArray wk = new org.json.JSONArray(), log = new org.json.JSONArray(LogStore.sp(c).getString("log", "[]"));
            for (int i = 0; i < log.length(); i++) {
                JSONObject l = log.getJSONObject(i); if (l.optLong("ts", 0) < from) continue;
                org.json.JSONArray f = new org.json.JSONArray(); org.json.JSONArray m = l.optJSONArray("m");
                if (m != null) for (int j = 0; j < m.length(); j++) { JSONObject x = m.getJSONObject(j); if (!x.optBoolean("ok", false) && !x.optBoolean("a", false)) f.put(x.optString("n")); }
                wk.put(new JSONObject().put("d", l.optString("d")).put("t", l.optString("t")).put("n", l.optString("n")).put("ok", l.isNull("ok") || !l.has("ok") ? JSONObject.NULL : l.optBoolean("ok")).put("meal", l.optBoolean("meal", false)).put("f", f));
            }
            org.json.JSONArray sn = new org.json.JSONArray(), all = new org.json.JSONArray(LogStore.sp(c).getString("sintn", "[]"));
            for (int i = 0; i < all.length(); i++) { JSONObject x = all.getJSONObject(i); if (x.optLong("ts", 0) >= from) sn.put(x); }
            o.put("week", wk).put("sint", sn).put("ts", System.currentTimeMillis());
            post("/state", new JSONObject().put("c", code(c)).put("d", o.toString()));
        } catch (Throwable e) {}
    }
    static String nome(Context c){ return LogStore.sp(c).getString("cname", ""); }
    /** Cuidador: publica nome, aparelho, bateria e ultima localizacao. sync=true bloqueia (uso dentro do servico de push). */
    public static void profile(Context c, boolean sync){
        try {
            if (!"cuidador".equals(mode(c)) || !valid(code(c))) return;
            final JSONObject o = new JSONObject().put("c", code(c)).put("name", nome(c)).put("dev", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
            android.content.Intent b = c.registerReceiver(null, new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
            if (b != null) { int lv = b.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1), sc = b.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100); if (lv >= 0) o.put("bat", Math.round(100f * lv / sc)); }
            Runnable go = () -> {
                try {
                    JSONObject l = Loc.last(c);
                    if (l != null) o.put("lat", l.getDouble("lat")).put("lon", l.getDouble("lon")).put("place", l.optString("place"));
                    String t = LogStore.sp(c).getString("ftok", "");
                    if (t.isEmpty()) { try { t = com.google.android.gms.tasks.Tasks.await(com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken(), 8, java.util.concurrent.TimeUnit.SECONDS); LogStore.sp(c).edit().putString("ftok", t).apply(); } catch (Throwable e) {} }
                    if (t.isEmpty()) return;
                    o.put("t", t); postNow("/prof", o);
                } catch (Throwable e) {}
            };
            if (sync) go.run(); else new Thread(go).start();
        } catch (Throwable e) {}
    }
    static void postNow(String path, JSONObject o){
        try {
            HttpURLConnection h = (HttpURLConnection) new URL(BASE + path).openConnection();
            h.setRequestMethod("POST"); h.setConnectTimeout(8000); h.setReadTimeout(8000); h.setDoOutput(true);
            h.setRequestProperty("content-type", "application/json");
            try (OutputStream os = h.getOutputStream()) { os.write(o.toString().getBytes("UTF-8")); }
            h.getResponseCode(); h.disconnect();
        } catch (Throwable e) {}
    }
    /** Cuidador: baixa a lista de cuidadores e guarda em "profs". */
    public static void profs(Context c, Runnable done){
        final String code = code(c); if (!valid(code)) return;
        new Thread(() -> {
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(BASE + "/profs").openConnection();
                h.setRequestMethod("POST"); h.setConnectTimeout(8000); h.setReadTimeout(8000); h.setDoOutput(true);
                h.setRequestProperty("content-type", "application/json");
                try (OutputStream os = h.getOutputStream()) { os.write(new JSONObject().put("c", code).toString().getBytes("UTF-8")); }
                if (h.getResponseCode() < 400) {
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(); byte[] b = new byte[4096]; int n; java.io.InputStream in = h.getInputStream();
                    while ((n = in.read(b)) > 0) bo.write(b, 0, n);
                    LogStore.sp(c).edit().putString("profs", new JSONObject(bo.toString("UTF-8")).optJSONArray("d").toString()).apply();
                    if (done != null) done.run();
                }
                h.disconnect();
            } catch (Throwable e) {}
        }).start();
    }
    public static void locReq(Context c, String id){
        try { String code = code(c); if (valid(code) && id != null && id.matches("[A-Za-z0-9_-]{4,20}")) post("/locreq", new JSONObject().put("c", code).put("id", id)); } catch (Throwable e) {}
    }
}
