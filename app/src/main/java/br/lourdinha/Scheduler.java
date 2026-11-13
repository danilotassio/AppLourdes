package br.lourdinha;
import android.app.*;
import android.content.*;
import java.util.Calendar;
public class Scheduler {
    public static final String[] TIMES = {"06:00","10:00","12:00","14:00","18:00","22:00","00:00","02:00"};
    public static final String[] NAMES = {"amarela","verde","laranja","azul","rosa","roxa","cinza","vermelha"};
    public static final int[] COLORS = {0xFFF2D000,0xFF2E9E2E,0xFFF08A00,0xFF2F7FE0,0xFFE0489E,0xFF8A55D0,0xFF707070,0xFFD83030};
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
    public static void scheduleAll(Context c){ for (int i = 0; i < TIMES.length; i++) { try { schedule(c, i); } catch (Throwable e) {} } }
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
