package br.lourdinha;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import org.json.JSONObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
/** Localizacao enviada junto com o status (somente se o cuidador ativou). Chamar fora da thread principal. */
public class Loc {
    public static boolean on(Context c){
        try { return new JSONObject(LogStore.sp(c).getString("cfg", "{}")).optBoolean("loc", false); } catch (Throwable e) { return false; }
    }
    public static boolean allowed(Context c){ return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED; }
    /** Devolve " Local: https://maps.google.com/?q=lat,lon" ou "" se desligado/indisponivel. */
    public static String link(Context c){
        try {
            if (!on(c) || !allowed(c)) return "";
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (Build.VERSION.SDK_INT >= 30 && (best == null || System.currentTimeMillis() - best.getTime() > 5 * 60000L)) {
                final Location[] box = new Location[1]; final CountDownLatch lt = new CountDownLatch(1);
                String prov = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
                lm.getCurrentLocation(prov, null, c.getMainExecutor(), l -> { box[0] = l; lt.countDown(); });
                lt.await(8, TimeUnit.SECONDS);
                if (box[0] != null) best = box[0];
            }
            if (best == null) return "";
            String place = "";
            try {
                if (android.location.Geocoder.isPresent()) {
                    java.util.List<android.location.Address> ad = new android.location.Geocoder(c, new java.util.Locale("pt", "BR")).getFromLocation(best.getLatitude(), best.getLongitude(), 1);
                    if (ad != null && !ad.isEmpty()) {
                        android.location.Address a = ad.get(0);
                        String rua = a.getThoroughfare(), num = a.getSubThoroughfare(), bairro = a.getSubLocality(), cid = a.getLocality() != null ? a.getLocality() : a.getSubAdminArea();
                        StringBuilder sb = new StringBuilder();
                        if (rua != null) sb.append(rua).append(num != null && !num.equals(rua) ? ", " + num : "");
                        if (bairro != null) sb.append(sb.length() > 0 ? " - " : "").append(bairro);
                        if (cid != null) sb.append(sb.length() > 0 ? ", " : "").append(cid);
                        place = sb.toString();
                    }
                }
            } catch (Throwable e) {}
            return " Local: " + (place.isEmpty() ? "" : place + " ") + "https://maps.google.com/?q=" + String.format(java.util.Locale.US, "%.5f,%.5f", best.getLatitude(), best.getLongitude());
        } catch (Throwable e) { return ""; }
    }
    /** Percentual de bateria, ex.: " Bateria: 63%." (vazio se indisponivel). */
    public static String battery(Context c){
        try {
            android.content.Intent b = c.registerReceiver(null, new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
            if (b == null) return "";
            int lv = b.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1), sc = b.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100);
            return lv < 0 ? "" : " Bateria: " + Math.round(100f * lv / sc) + "%.";
        } catch (Throwable e) { return ""; }
    }
}
