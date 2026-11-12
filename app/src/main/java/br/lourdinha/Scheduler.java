package br.lourdinha;
import android.app.*;
import android.content.*;
import java.util.Calendar;
public class Scheduler {
    public static final String[] TIMES = {"06:00","10:00","12:00","14:00","18:00","22:00","00:00","02:00"};
    public static void scheduleAll(Context c){ for (int i = 0; i < TIMES.length; i++) schedule(c, i); }
    public static void schedule(Context c, int i){
        String[] p = TIMES[i].split(":");
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(p[0]));
        cal.set(Calendar.MINUTE, Integer.parseInt(p[1]));
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);
        Intent in = new Intent(c, AlarmReceiver.class).putExtra("i", i);
        PendingIntent pi = PendingIntent.getBroadcast(c, i, in, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        } catch (Exception e) { am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi); }
    }
}
