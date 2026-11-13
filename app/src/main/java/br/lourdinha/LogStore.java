package br.lourdinha;
import android.content.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;
public class LogStore {
    static SharedPreferences sp(Context c){ return c.getSharedPreferences("lourdinha", Context.MODE_PRIVATE); }
    public static void add(Context c, String time, int idx, Boolean ok){ add(c, time, idx, ok, null); }
    public static synchronized void add(Context c, String time, int idx, Boolean ok, String metaJson){
        try {
            JSONArray a = new JSONArray(sp(c).getString("log", "[]"));
            JSONObject o = new JSONObject();
            o.put("d", new SimpleDateFormat("dd/MM", Locale.getDefault()).format(new Date()));
            o.put("ts", System.currentTimeMillis());
            o.put("t", time);
            o.put("n", idx >= 0 && idx < Scheduler.NAMES.length ? Scheduler.NAMES[idx].toUpperCase() : "");
            if (ok == null) o.put("ok", JSONObject.NULL); else o.put("ok", ok.booleanValue());
            if (metaJson != null) o.put("m", new JSONArray(metaJson));
            a.put(o);
            sp(c).edit().putString("log", prune(a).toString()).apply();
        } catch (Exception e) {}
    }
    public static synchronized void addMeal(Context c, String time, String name, Boolean ok){
        try {
            JSONArray a = new JSONArray(sp(c).getString("log", "[]"));
            JSONObject o = new JSONObject();
            o.put("d", new SimpleDateFormat("dd/MM", Locale.getDefault()).format(new Date()));
            o.put("ts", System.currentTimeMillis());
            o.put("t", time); o.put("n", name);
            if (ok == null) o.put("ok", JSONObject.NULL); else o.put("ok", ok.booleanValue());
            o.put("meal", true);
            a.put(o);
            sp(c).edit().putString("log", prune(a).toString()).apply();
        } catch (Exception e) {}
    }
    // guarda os registros dos últimos 12 meses (limite de segurança: 8000 itens)
    static JSONArray prune(JSONArray a) throws JSONException {
        long cut = System.currentTimeMillis() - 366L * 24 * 60 * 60 * 1000;
        JSONArray b = new JSONArray();
        for (int k = Math.max(0, a.length() - 8000); k < a.length(); k++) {
            JSONObject o = a.getJSONObject(k);
            if (!o.has("ts") || o.getLong("ts") >= cut) b.put(o);
        }
        return b;
    }
    public static String get(Context c){ return sp(c).getString("log", "[]"); }
    public static void clear(Context c){ sp(c).edit().putString("log", "[]").apply(); }
}
