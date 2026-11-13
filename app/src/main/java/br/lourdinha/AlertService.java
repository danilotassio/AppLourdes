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
            if ("locreq".equals(d.get("kind"))) { Push.profile(this, true); return; }
            String title = d.get("title") == null ? "AppLourdinha" : d.get("title"), body = d.get("body") == null ? "" : d.get("body");
            String lb = body.toLowerCase(), kind = (lb.contains("nao confirmou") || lb.contains("nao tomou")) ? "remedio" : lb.contains("esta bem") ? "bem" : lb.matches(".*registrou (dor|enjoo|tontura).*") ? "sintoma" : lb.contains("bateria") ? "bateria" : lb.contains("sinal de vida") ? "vida" : lb.contains("para comprar") ? "compra" : lb.contains("lembrete") ? "consulta" : lb.contains("resumo da semana") ? "resumo" : lb.contains("esta ligando") ? "contato" : "outro";
            if (LogStore.sp(this).getString("cflt", "[]").contains("\"" + kind + "\"")) return;
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
              .setContentIntent(pi).setAutoCancel(true).setCategory(Notification.CATEGORY_MESSAGE).setVisibility(Notification.VISIBILITY_PUBLIC).setPriority(Notification.PRIORITY_MAX).setDefaults(Notification.DEFAULT_ALL);
            nm.notify((int) (System.currentTimeMillis() % 100000) + 1000, nb.build());
        } catch (Throwable e) {}
    }
}
