package br.lourdinha;
import android.app.*;
import android.content.*;
import java.util.*;
import java.text.SimpleDateFormat;
public class Scheduler {
    public static final String[] TIMES = {"06:00","10:00","12:00","14:00","18:00","22:00","00:00","02:00"};
    public static final String[] NAMES = {"amarela","verde","laranja","azul","rosa","roxa","cinza","vermelha"};
    public static final int[] COLORS = {0xFFF2D000,0xFF2E9E2E,0xFFF08A00,0xFF2F7FE0,0xFFE0489E,0xFF8A55D0,0xFF707070,0xFFD83030};
    public static String apptDate(Context c){ String v = LogStore.sp(c).getString("appt", "2026-12-02"); return v.matches("\\d{4}-\\d\\d-\\d\\d") ? v : "2026-12-02"; }
    public static String apptTime(Context c){ String v = LogStore.sp(c).getString("apptT", "07:00"); return v.matches("\\d\\d:\\d\\d") ? v : "07:00"; }
    static long apptMillis(Context c) throws Exception { return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(apptDate(c) + " " + apptTime(c)).getTime(); }
    /** 0 = noite anterior aos exames (20h; exames = 3 dias antes da consulta); 1 = dia da consulta, 1h30 antes. */
    public static boolean actOn(Context c){ return LogStore.sp(c).getBoolean("act", false); }
    public static String actTime(Context c, int k){ String v = LogStore.sp(c).getString("act" + k, k == 0 ? "09:00" : "16:00"); return v.matches("\\d\\d:\\d\\d") ? v : (k == 0 ? "09:00" : "16:00"); }
    public static long remAtAppt(Context c) throws Exception { return apptMillis(c); }
    public static long remAt(Context c, int i) throws Exception {
        if (i >= 2) {
            String[] p = actTime(c, i - 2).split(":");
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(p[0])); cal.set(Calendar.MINUTE, Integer.parseInt(p[1])); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
            if (cal.getTimeInMillis() <= System.currentTimeMillis() + 1000) cal.add(Calendar.DAY_OF_YEAR, 1);
            return cal.getTimeInMillis();
        }
        long a = apptMillis(c);
        if (i == 1) return a - 90L * 60000L;
        Calendar cal = Calendar.getInstance(); cal.setTimeInMillis(a);
        cal.add(Calendar.DAY_OF_YEAR, -4); cal.set(Calendar.HOUR_OF_DAY, 20); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
    static String horaFalada(Context c){ String[] p = apptTime(c).split(":"); int h = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]); return m == 0 ? h + " horas" : h + " e " + m; }
    public static String remTitle(Context c, int i){ if (i == 2) return "Hora do alongamento"; if (i == 3) return "Hora da caminhada leve"; return i == 0 ? "Amanhã: exames" : "Hoje: consulta às " + apptTime(c); }
    public static String remMsg(Context c, int i){ if (i == 2) return "Lourdinha, hora do alongamento! Faça com calma, sem forçar."; if (i == 3) return "Lourdinha, hora da caminhada leve! Vá no seu ritmo."; return i == 0 ? "Lourdinha, amanhã é o dia dos exames. Não esqueça!" : "Bom dia, Lourdinha! Hoje é o dia da sua consulta, às " + horaFalada(c) + "!"; }
    public static void scheduleReminders(Context c){
        for (int i = 0; i < 4; i++) {
            try {
                PendingIntent pi = PendingIntent.getBroadcast(c, 500 + i, new Intent(c, AlarmReceiver.class).putExtra("rem", i), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
                if (i >= 2 && !actOn(c)) { am.cancel(pi); continue; }
                long t = remAt(c, i);
                if (t <= System.currentTimeMillis()) { am.cancel(pi); continue; }
                PendingIntent spi = PendingIntent.getActivity(c, 600 + i, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(t, spi), pi);
            } catch (Throwable e) {}
        }
    }
    public static final String[] MEAL_TIME = {"06:15", "13:00", "19:00"};
    public static String mealTime(Context c, int m){ String v = LogStore.sp(c).getString("mt" + m, MEAL_TIME[m]); return v.matches("\\d\\d:\\d\\d") ? v : MEAL_TIME[m]; }
    public static final String[] MEAL_NAME = {"café da manhã", "almoço", "janta"};
    public static final String[] MEAL_LOG = {"CAFÉ DA MANHÃ", "ALMOÇO", "JANTA"};
    public static final String[] MEAL_WAKE = {"Bom dia, Lourdinha! Hora do café da manhã!", "Boa tarde, Lourdinha! Hora do almoço!", "Boa noite, Lourdinha! Hora da janta!"};
    public static final String[] MEAL_Q = {"Lourdinha, você tomou o café da manhã mesmo?", "Lourdinha, você almoçou mesmo?", "Lourdinha, você jantou mesmo?"};
    public static boolean mealsOn(Context c){ return LogStore.sp(c).getBoolean("meals", true); }
    public static void cancelMeals(Context c){
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        for (int m = 0; m < MEAL_TIME.length; m++) {
            try {
                am.cancel(PendingIntent.getBroadcast(c, 800 + m, new Intent(c, AlarmReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
                am.cancel(PendingIntent.getBroadcast(c, 900 + m, new Intent(c, AlarmReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            } catch (Throwable e) {}
        }
    }
    public static void scheduleMeal(Context c, int m){
        String[] p = mealTime(c, m).split(":");
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(p[0])); cal.set(Calendar.MINUTE, Integer.parseInt(p[1]));
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 1000) cal.add(Calendar.DAY_OF_YEAR, 1);
        PendingIntent pi = PendingIntent.getBroadcast(c, 800 + m, new Intent(c, AlarmReceiver.class).putExtra("meal", m).putExtra("stage", 0), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent spi = PendingIntent.getActivity(c, 850 + m, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try { ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).setAlarmClock(new AlarmManager.AlarmClockInfo(cal.getTimeInMillis(), spi), pi); } catch (Throwable e) {}
    }
    public static void mealQ(Context c, int m, int att, long delay, boolean test){
        PendingIntent pi = PendingIntent.getBroadcast(c, 900 + m, new Intent(c, AlarmReceiver.class).putExtra("meal", m).putExtra("stage", 1).putExtra("att", att).putExtra("test", test), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent spi = PendingIntent.getActivity(c, 950 + m, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try { ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).setAlarmClock(new AlarmManager.AlarmClockInfo(System.currentTimeMillis() + delay, spi), pi); } catch (Throwable e) {}
    }
    public static String no(Context c, int idx){
        SharedPreferences sp = LogStore.sp(c);
        int n = sp.getInt("att", 0) + 1;
        if (n >= 3) { LogStore.add(c, TIMES[idx], idx, false); sp.edit().putInt("att", 0).apply(); return "final"; }
        sp.edit().putInt("att", n).apply();
        retry(c, idx, n, 10 * 60 * 1000L);
        return "retry";
    }
    public static void resetAtt(Context c){ LogStore.sp(c).edit().putInt("att", 0).apply(); }
    public static void retry(Context c, int i, int n, long delay){
        Intent in = new Intent(c, AlarmReceiver.class).putExtra("i", i).putExtra("attempt", n);
        PendingIntent pi = PendingIntent.getBroadcast(c, 400 + i, in, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent spi = PendingIntent.getActivity(c, 200 + i, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try { ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).setAlarmClock(new AlarmManager.AlarmClockInfo(System.currentTimeMillis() + delay, spi), pi); } catch (Throwable e) {}
    }
    public static void scheduleAll(Context c){ if ("cuidador".equals(LogStore.sp(c).getString("mode", ""))) return; try { Watch.schedule(c); } catch (Throwable e) {} scheduleReminders(c); if (mealsOn(c)) { for (int m = 0; m < MEAL_TIME.length; m++) { try { scheduleMeal(c, m); } catch (Throwable e) {} } } else cancelMeals(c); for (int i = 0; i < TIMES.length; i++) { try { schedule(c, i); } catch (Throwable e) {} } }
    public static void schedule(Context c, int i){
        String[] p = TIMES[i].split(":");
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(p[0]));
        cal.set(Calendar.MINUTE, Integer.parseInt(p[1]));
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 1000) cal.add(Calendar.DAY_OF_YEAR, 1);
        Intent in = new Intent(c, AlarmReceiver.class).putExtra("i", i);
        PendingIntent pi = PendingIntent.getBroadcast(c, i, in, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent show = new Intent(c, MainActivity.class);
        PendingIntent spi = PendingIntent.getActivity(c, 200 + i, show, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        try { am.setAlarmClock(new AlarmManager.AlarmClockInfo(cal.getTimeInMillis(), spi), pi); } catch (Throwable e) {}
    }
}
