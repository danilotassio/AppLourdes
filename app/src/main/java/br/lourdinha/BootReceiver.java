package br.lourdinha;
import android.content.*;
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent in){ Scheduler.scheduleAll(c); }
}
