package br.lourdinha;
import android.app.*;
import android.content.*;
import android.os.BatteryManager;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;
/** Vigia a cada 30 min: bateria, sinal de vida, lembretes de consulta/exames e resumo semanal (tudo por SMS). */
public class Watch {
    static final long EVERY = 30L * 60 * 1000;
    public static void schedule(Context c){
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            PendingIntent pi = PendingIntent.getBroadcast(c, 990, new Intent(c, AlarmReceiver.class).putExtra("watch", 1), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            long t = System.currentTimeMillis() + EVERY;
            try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi); } catch (Throwable e) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi); }
        } catch (Throwable e) {}
    }
    public static void touch(Context c){ LogStore.sp(c).edit().putLong("seen", System.currentTimeMillis()).apply(); }
    static boolean sent(Context c, String msg){ return Sms.on(c) && Sms.send(c, msg) > 0; }
    public static void run(Context c){
        try {
            SharedPreferences sp = LogStore.sp(c);
            long now = System.currentTimeMillis();
            if (!sp.contains("seen")) touch(c);
            try { Push.cfgFetch(c, null); } catch (Throwable e) {}
            // 1) bateria
            try {
                Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (b != null) {
                    int lv = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    int st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    boolean chg = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
                    int pct = lv < 0 ? 100 : Math.round(100f * lv / sc);
                    if (pct <= 20 && !chg) { if (!sp.getBoolean("batSent", false) && sent(c, "AppLourdinha: a bateria do celular da Lourdinha esta em " + pct + "%. Coloque para carregar, senao os alarmes podem falhar.")) sp.edit().putBoolean("batSent", true).apply(); }
                    else if (pct >= 35 || chg) sp.edit().putBoolean("batSent", false).apply();
                }
            } catch (Throwable e) {}
            // 2) sinal de vida
            try {
                int h = sp.getInt("lifeH", 12);
                long seen = sp.getLong("seen", now);
                if (h > 0 && now - seen > h * 3600000L && sp.getLong("lifeSent", 0) < seen) {
                    long horas = (now - seen) / 3600000L;
                    if (sent(c, "AppLourdinha: sem sinal de vida. O celular da Lourdinha nao e usado nem respondido ha " + horas + " horas. Por favor, verifique.")) sp.edit().putLong("lifeSent", now).apply();
                }
            } catch (Throwable e) {}
            // 2b) remedios para comprar: lembrete por SMS a cada 2 horas enquanto houver pendencia
            try {
                String buy = sp.getString("buy", "");
                if (buy.isEmpty()) sp.edit().putLong("buySent", 0).apply();
                else if (now - sp.getLong("buySent", 0) >= 2L * 3600000L - 60000L && sent(c, "AppLourdinha: remedios para comprar: " + buy + ".")) sp.edit().putLong("buySent", now).apply();
            } catch (Throwable e) {}
            Calendar cal = Calendar.getInstance();
            SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            // 3) lembrete de consulta e exames (2 dias antes)
            try {
                if (cal.get(Calendar.HOUR_OF_DAY) >= 8) {
                    String today = day.format(new Date());
                    Calendar a = Calendar.getInstance(); a.setTimeInMillis(Scheduler.remAtAppt(c));
                    Calendar co = (Calendar) a.clone(); co.add(Calendar.DAY_OF_YEAR, -2);
                    if (today.equals(day.format(co.getTime())) && !today.equals(sp.getString("rmCo", ""))
                        && sent(c, "AppLourdinha: lembrete: a consulta da Lourdinha e em 2 dias (" + new SimpleDateFormat("dd/MM", Locale.getDefault()).format(a.getTime()) + " as " + Scheduler.apptTime(c) + ").")) sp.edit().putString("rmCo", today).apply();
                    Calendar ex = (Calendar) a.clone(); ex.add(Calendar.DAY_OF_YEAR, -5);
                    Calendar exd = (Calendar) a.clone(); exd.add(Calendar.DAY_OF_YEAR, -3);
                    if (today.equals(day.format(ex.getTime())) && !today.equals(sp.getString("rmEx", ""))
                        && sent(c, "AppLourdinha: lembrete: os exames da Lourdinha sao em 2 dias (" + new SimpleDateFormat("dd/MM", Locale.getDefault()).format(exd.getTime()) + ").")) sp.edit().putString("rmEx", today).apply();
                }
            } catch (Throwable e) {}
            // 4) resumo semanal (domingo, depois das 20h)
            try {
                String wk = cal.get(Calendar.YEAR) + "w" + cal.get(Calendar.WEEK_OF_YEAR);
                if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY && cal.get(Calendar.HOUR_OF_DAY) >= 20 && !wk.equals(sp.getString("wkSent", ""))
                    && sent(c, weekly(c))) sp.edit().putString("wkSent", wk).apply();
            } catch (Throwable e) {}
        } catch (Throwable e) {}
    }
    static String weekly(Context c) throws Exception {
        SharedPreferences sp = LogStore.sp(c);
        long from = System.currentTimeMillis() - 7L * 86400000L;
        JSONArray a = new JSONArray(sp.getString("log", "[]"));
        int tot = 0, ok = 0, mt = 0, mo = 0;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            if (o.optLong("ts", 0) < from) continue;
            boolean good = o.has("ok") && !o.isNull("ok") && o.optBoolean("ok");
            if (o.optBoolean("meal", false)) { mt++; if (good) mo++; } else { tot++; if (good) ok++; }
        }
        int en = 0, dor = 0, ton = 0;
        JSONArray s = new JSONArray(sp.getString("sintn", "[]"));
        for (int i = 0; i < s.length(); i++) {
            JSONObject o = s.getJSONObject(i);
            if (o.optLong("ts", 0) < from) continue;
            String k = o.optString("k");
            if (k.equals("enjoo")) en++; else if (k.equals("dor")) dor++; else if (k.equals("tontura")) ton++;
        }
        String r = "AppLourdinha - resumo da semana: " + (tot > 0 ? Math.round(100f * ok / tot) + "% dos remedios (" + ok + " de " + tot + ")" : "sem registros de remedios");
        if (mt > 0) r += ", refeicoes " + mo + " de " + mt;
        r += ", " + en + (en == 1 ? " enjoo" : " enjoos") + ", " + dor + (dor == 1 ? " dor" : " dores") + ", " + ton + (ton == 1 ? " tontura." : " tonturas.");
        return r;
    }
}
