package br.lourdinha;
import android.app.*;
import android.content.Intent;
import android.os.Build;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import org.json.*;
import java.util.Map;
public class AlertService extends FirebaseMessagingService {
    @Override public void onNewToken(String t){ Push.register(this); }
    @Override public void onMessageReceived(RemoteMessage m){
        try {
            if (!"cuidador".equals(Push.mode(this))) return;
            Map<String, String> d = m.getData();
            String title = d.get("title") == null ? "AppLourdinha" : d.get("title"), body = d.get("body") == null ? "" : d.get("body");
            JSONArray a = new JSONArray(LogStore.sp(this).getString("alerts", "[]"));
            JSONObject o = new JSONObject().put("ts", System.currentTimeMillis()).put("title", title).put("body", body);
            JSONArray b = new JSONArray(); b.put(o);
            for (int i = 0; i < Math.min(49, a.length()); i++) b.put(a.get(i));
            LogStore.sp(this).edit().putString("alerts", b.toString()).putString("alertNew", o.toString()).apply();
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel("avisos", "Avisos da Lourdinha", NotificationManager.IMPORTANCE_HIGH));
            PendingIntent pi = PendingIntent.getActivity(this, 77, new Intent(this, MainActivity.class).putExtra("alert", 1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder nb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "avisos") : new Notification.Builder(this);
            nb.setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
              .setContentIntent(pi).setFullScreenIntent(pi, true).setAutoCancel(true).setPriority(Notification.PRIORITY_MAX).setDefaults(Notification.DEFAULT_ALL);
            nm.notify((int) (System.currentTimeMillis() % 100000) + 1000, nb.build());
        } catch (Throwable e) {}
    }
}
