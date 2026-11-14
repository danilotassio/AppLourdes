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
    public static boolean allowed(Context c){ return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED; }
    /** Devolve " Local: https://maps.google.com/?q=lat,lon" ou "" se desligado/indisponivel. */
    public static String link(Context c){
        try {
            if (!LogStore.sp(c).contains("cfg")) Push.cfgFetchNow(c);
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
            String place = place(c, best.getLatitude(), best.getLongitude());
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
    /** Ultima posicao conhecida: {lat, lon, place} ou null (sem espera, sem exigir o interruptor). */
    public static JSONObject last(Context c){
        try {
            if (!allowed(c)) return null;
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String p : lm.getProviders(true)) { Location l = lm.getLastKnownLocation(p); if (l != null && (best == null || l.getTime() > best.getTime())) best = l; }
            if (best == null) return null;
            String place = place(c, best.getLatitude(), best.getLongitude());
            return new JSONObject().put("lat", best.getLatitude()).put("lon", best.getLongitude()).put("place", place);
        } catch (Throwable e) { return null; }
    }

    /** Endereco "Rua, 123 - Bairro, Cidade": Geocoder do aparelho e, se falhar, OpenStreetMap (Nominatim). */
    public static String place(Context c, double lat, double lon){
        String p = "";
        try {
            if (android.location.Geocoder.isPresent()) {
                java.util.List<android.location.Address> ad = new android.location.Geocoder(c, new java.util.Locale("pt", "BR")).getFromLocation(lat, lon, 1);
                if (ad != null && !ad.isEmpty()) {
                    android.location.Address a = ad.get(0);
                    p = fmt(a.getThoroughfare(), a.getSubThoroughfare(), a.getSubLocality(), a.getLocality() != null ? a.getLocality() : a.getSubAdminArea());
                }
            }
        } catch (Throwable e) {}
        if (!p.isEmpty()) return p;
        try {
            java.net.HttpURLConnection h = (java.net.HttpURLConnection) new java.net.URL(String.format(java.util.Locale.US, "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%.6f&lon=%.6f&zoom=18&addressdetails=1&accept-language=pt-BR", lat, lon)).openConnection();
            h.setConnectTimeout(6000); h.setReadTimeout(6000); h.setRequestProperty("User-Agent", "AppLourdinha/1.0 (cuidado familiar)");
            if (h.getResponseCode() < 400) {
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(); byte[] b = new byte[2048]; int n; java.io.InputStream in = h.getInputStream();
                while ((n = in.read(b)) > 0) bo.write(b, 0, n);
                JSONObject ad = new JSONObject(bo.toString("UTF-8")).optJSONObject("address");
                if (ad != null) {
                    String bairro = ad.optString("suburb", ad.optString("neighbourhood", ad.optString("quarter", "")));
                    String cid = ad.optString("city", ad.optString("town", ad.optString("village", ad.optString("municipality", ""))));
                    p = fmt(ad.optString("road", ""), ad.optString("house_number", ""), bairro, cid);
                }
            }
            h.disconnect();
        } catch (Throwable e) {}
        return p;
    }
    private static String fmt(String rua, String num, String bairro, String cid){
        StringBuilder sb = new StringBuilder();
        if (rua != null && !rua.isEmpty()) sb.append(rua).append(num != null && !num.isEmpty() && !num.equals(rua) ? ", " + num : "");
        if (bairro != null && !bairro.isEmpty()) sb.append(sb.length() > 0 ? " - " : "").append(bairro);
        if (cid != null && !cid.isEmpty()) sb.append(sb.length() > 0 ? ", " : "").append(cid);
        return sb.toString();
    }
}
