package br.lourdinha;
import android.app.*;
import android.content.*;
public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent in){
        int i = in.getIntExtra("i", 0);
        Scheduler.schedule(c, i); // reagenda para amanhã
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("remedio", "Remédios", NotificationManager.IMPORTANCE_HIGH);
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        }
        Intent act = new Intent(c, MainActivity.class).putExtra("p", Scheduler.TIMES[i]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 100 + i, act, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = android.os.Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, "remedio") : new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Hora do remédio!")
         .setContentText("Toque para ver a cor da bolsa").setContentIntent(pi).setFullScreenIntent(pi, true)
         .setCategory(Notification.CATEGORY_ALARM).setAutoCancel(true).setPriority(Notification.PRIORITY_MAX);
        nm.notify(i, b.build());
        try { c.startActivity(act); } catch (Exception e) {}
    }
}
