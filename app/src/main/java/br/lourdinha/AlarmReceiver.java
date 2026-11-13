package br.lourdinha;
import android.content.*;
import android.os.Build;
public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent in){
        // celular em modo cuidador nunca toca despertador nem agenda nada (alarmes antigos ficam sem efeito)
        if ("cuidador".equals(LogStore.sp(c).getString("mode", ""))) return;
        if (in.hasExtra("watch")) { Watch.run(c); Watch.schedule(c); return; }
        if (in.hasExtra("meal")) {
            int m = in.getIntExtra("meal", 0), st = in.getIntExtra("stage", 0), at = in.getIntExtra("att", 1);
            boolean t = in.getBooleanExtra("test", false);
            if (!t && !Scheduler.mealsOn(c)) return;
            if (st == 0) { if (!t) Scheduler.scheduleMeal(c, m); Scheduler.mealQ(c, m, 1, t ? 10000L : 25 * 60 * 1000L, t); }
            Intent r = new Intent(c, AlarmService.class).putExtra("meal", m).putExtra("stage", st).putExtra("att", at).putExtra("test", t);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(r); else c.startService(r);
            return;
        }
        if (in.hasExtra("rem")) {
            if (in.getIntExtra("rem", 0) >= 2) { if (!Scheduler.actOn(c)) return; Scheduler.scheduleReminders(c); }
            Intent r = new Intent(c, AlarmService.class).putExtra("rem", in.getIntExtra("rem", 0));
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(r); else c.startService(r);
            return;
        }
        int i = in.getIntExtra("i", 0);
        try { Push.stateRefresh(c); } catch (Throwable e) {}
        if (!in.hasExtra("attempt")) { Scheduler.schedule(c, i); Scheduler.resetAtt(c); }
        Intent s = new Intent(c, AlarmService.class).putExtra("i", i);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s); else c.startService(s);
    }
}
