package br.lourdinha;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SmsManager;
import java.text.Normalizer;
import java.util.*;
public class Sms {
    public static final String[] FIXED = {"81996739556", "81989574545"};
    public static boolean on(Context c){ return LogStore.sp(c).getBoolean("sms", true); }
    public static List<String> targets(Context c){
        LinkedHashSet<String> s = new LinkedHashSet<>(Arrays.asList(FIXED));
        for (String n : LogStore.sp(c).getString("smsx", "").split(",")) { n = n.replaceAll("[^0-9]", ""); if (n.length() >= 10 && n.length() <= 13) s.add(n); }
        return new ArrayList<>(s);
    }
    static String plain(String t){ return Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", ""); }
    /** Envia o SMS para todos os números. Devolve quantos foram enviados, ou -1 se falta a permissão. */
    public static int send(Context c, String msg){
        Push.send(c, msg);
        if (c.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return -1;
        int ok = 0;
        try {
            SmsManager sm = Build.VERSION.SDK_INT >= 31 ? c.getSystemService(SmsManager.class) : SmsManager.getDefault();
            ArrayList<String> parts = sm.divideMessage(plain(msg));
            for (String n : targets(c)) {
                try { sm.sendMultipartTextMessage(n, null, parts, null, null); ok++; } catch (Throwable e) {}
            }
        } catch (Throwable e) {}
        return ok;
    }
    /** Envia apenas para os dois números fixos (alertas de segurança de senha). */
    public static int sendFixed(Context c, String msg){
        Push.send(c, msg);
        if (c.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return -1;
        int ok = 0;
        try {
            SmsManager sm = Build.VERSION.SDK_INT >= 31 ? c.getSystemService(SmsManager.class) : SmsManager.getDefault();
            ArrayList<String> parts = sm.divideMessage(plain(msg));
            for (String n : FIXED) { try { sm.sendMultipartTextMessage(n, null, parts, null, null); ok++; } catch (Throwable e) {} }
        } catch (Throwable e) {}
        return ok;
    }
    /** Chamado quando um horário de remédio termina sem confirmação (sem resposta ou "não consegui" após as tentativas). */
    public static void alert(Context c, String time, int idx){
        try {
            if (!on(c)) return;
            String key = new java.text.SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date()) + time;
            if (key.equals(LogStore.sp(c).getString("smsLast", ""))) return;
            LogStore.sp(c).edit().putString("smsLast", key).apply();
            String nome = idx >= 0 && idx < Scheduler.NAMES.length ? Scheduler.NAMES[idx] : "";
            send(c, "AppLourdinha: a Lourdinha NAO confirmou o remedio das " + time + (nome.isEmpty() ? "" : " (bolsa " + nome.toUpperCase() + ")") + ". Por favor, verifique.");
        } catch (Throwable e) {}
    }
}
