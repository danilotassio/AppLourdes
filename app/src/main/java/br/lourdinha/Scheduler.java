package br.lourdinha;
import android.app.*;
import android.content.*;
import java.util.Calendar;
public class Scheduler {
    public static final String[] TIMES = {"06:00","10:00","12:00","14:00","18:00","22:00","00:00","02:00"};
    public static final String[] NAMES = {"amarela","verde","laranja","azul","rosa","roxa","cinza","vermelha"};
    public static void scheduleAll(Context c){ for (int i = 0; i < TIMES.length; i++) schedule(c, i); }
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
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(cal.getTimeInMillis(), spi), pi);
    }
}
