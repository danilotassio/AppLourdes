package br.lourdinha;
import android.content.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;
public class LogStore {
    static SharedPreferences sp(Context c){ return c.getSharedPreferences("lourdinha", Context.MODE_PRIVATE); }
    public static synchronized void add(Context c, String time, int idx, Boolean ok){
        try {
            JSONArray a = new JSONArray(sp(c).getString("log", "[]"));
            JSONObject o = new JSONObject();
            o.put("d", new SimpleDateFormat("dd/MM", Locale.getDefault()).format(new Date()));
            o.put("t", time);
            o.put("n", idx >= 0 && idx < Scheduler.NAMES.length ? Scheduler.NAMES[idx].toUpperCase() : "");
            if (ok == null) o.put("ok", JSONObject.NULL); else o.put("ok", ok.booleanValue());
            a.put(o);
            JSONArray b = new JSONArray();
            for (int k = Math.max(0, a.length() - 200); k < a.length(); k++) b.put(a.get(k));
            sp(c).edit().putString("log", b.toString()).apply();
        } catch (Exception e) {}
    }
    public static String get(Context c){ return sp(c).getString("log", "[]"); }
    public static void clear(Context c){ sp(c).edit().putString("log", "[]").apply(); }
}
