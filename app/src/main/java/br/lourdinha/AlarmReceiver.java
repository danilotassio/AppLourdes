package br.lourdinha;
import android.content.*;
import android.os.Build;
public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent in){
        int i = in.getIntExtra("i", 0);
        Scheduler.schedule(c, i);
        Intent s = new Intent(c, AlarmService.class).putExtra("i", i);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s); else c.startService(s);
    }
}
