package br.lourdinha;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.WindowManager;
import android.webkit.*;
public class MainActivity extends Activity {
    WebView web; android.speech.tts.TextToSpeech tts; boolean ttsOk = false;
    Uri pendingCall;
    class Bridge {
        @JavascriptInterface public void ligar(String tel){
            if (tel == null || !tel.matches("[0-9+]{8,15}")) return;
            final Uri u = Uri.parse("tel:" + tel);
            runOnUiThread(() -> {
                if (checkSelfPermission(Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                    startActivity(new Intent(Intent.ACTION_CALL, u));
                else { pendingCall = u; requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, 2); }
            });
        }
        @JavascriptInterface public String answer(boolean ok, String time){ return answer2(ok, time, null); }
        @JavascriptInterface public String answer2(boolean ok, String time, String metaJson){
            int idx = java.util.Arrays.asList(Scheduler.TIMES).indexOf(time);
            String r = "ok";
            if (idx >= 0) {
                if (ok) { LogStore.add(MainActivity.this, time, idx, true, metaJson); Scheduler.resetAtt(MainActivity.this); }
                else r = Scheduler.no(MainActivity.this, idx);
            }
            startService(new Intent(MainActivity.this, AlarmService.class).setAction("STOP"));
            return r;
        }
        @JavascriptInterface public void setSound(boolean on){
            LogStore.sp(MainActivity.this).edit().putBoolean("snd", on).apply();
            if (!on) startService(new Intent(MainActivity.this, AlarmService.class).setAction("HUSH"));
        }
        @JavascriptInterface public boolean getSound(){ return LogStore.sp(MainActivity.this).getBoolean("snd", true); }
        @JavascriptInterface public void share(String text){
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
                try { startActivity(new Intent(i).setPackage("com.whatsapp")); }
                catch (Exception e) { try { startActivity(Intent.createChooser(i, "Enviar resumo")); } catch (Exception e2) {} }
            });
        }
        @JavascriptInterface public void silence(){
            runOnUiThread(() -> { try { if (tts != null) tts.stop(); } catch (Exception e) {} });
            startService(new Intent(MainActivity.this, AlarmService.class).setAction("UNDUCK"));
        }
        @JavascriptInterface public void hush(){ startService(new Intent(MainActivity.this, AlarmService.class).setAction("HUSH")); }
        @JavascriptInterface public void speak(String t){
            startService(new Intent(MainActivity.this, AlarmService.class).setAction("DUCK"));
            runOnUiThread(() -> {
                if (tts != null && ttsOk) tts.speak(t, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "u");
                else startService(new Intent(MainActivity.this, AlarmService.class).setAction("UNDUCK"));
            });
        }
        @JavascriptInterface public void stop(){ startService(new Intent(MainActivity.this, AlarmService.class).setAction("STOP")); }
        @JavascriptInterface public void test(){
            Intent s = new Intent(MainActivity.this, AlarmService.class).putExtra("i", 0).putExtra("test", true);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(s); else startService(s);
        }
        @JavascriptInterface public String getLog(){ return LogStore.get(MainActivity.this); }
        @JavascriptInterface public void clearLog(){ LogStore.clear(MainActivity.this); }
        @JavascriptInterface public void openSetting(String kind){
            runOnUiThread(() -> {
                try {
                    if ("bateria".equals(kind)) startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
                    else if ("tela".equals(kind) && Build.VERSION.SDK_INT >= 34) startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName())));
                    else if ("notif".equals(kind)) {
                        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
                        else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                    }
                } catch (Exception e) {
                    try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); } catch (Exception e2) {}
                }
            });
        }
    }
    @Override protected void onCreate(Bundle s){
        super.onCreate(s);
        final android.content.SharedPreferences cp = getSharedPreferences("crash", MODE_PRIVATE);
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> { cp.edit().putString("e", android.util.Log.getStackTraceString(e)).commit(); android.os.Process.killProcess(android.os.Process.myPid()); });
        String old = cp.getString("e", null);
        if (old != null) {
            cp.edit().remove("e").commit();
            android.widget.ScrollView sv = new android.widget.ScrollView(this);
            android.widget.TextView tv = new android.widget.TextView(this); tv.setTextSize(12); tv.setPadding(20,40,20,20); tv.setTextIsSelectable(true);
            tv.setText("ERRO ANTERIOR (mande print ao Danilo/Claude):\n\n" + old);
            sv.addView(tv); setContentView(sv); return;
        }
        try { onCreate2(); } catch (Throwable e) {
            android.widget.ScrollView sv = new android.widget.ScrollView(this);
            android.widget.TextView tv = new android.widget.TextView(this); tv.setTextSize(12); tv.setPadding(20,40,20,20);
            tv.setText("ERRO:\n\n" + android.util.Log.getStackTraceString(e));
            sv.addView(tv); setContentView(sv);
        }
    }
    void onCreate2(){
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true); ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);
        try { tts = new android.speech.tts.TextToSpeech(this, st -> { try { if (st == android.speech.tts.TextToSpeech.SUCCESS && tts != null) { Voice.apply(tts);
                tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener(){
                    public void onStart(String id){}
                    public void onDone(String id){ startService(new Intent(MainActivity.this, AlarmService.class).setAction("UNDUCK")); }
                    public void onError(String id){ startService(new Intent(MainActivity.this, AlarmService.class).setAction("UNDUCK")); }
                });
                ttsOk = true; } } catch (Throwable e) {} }); } catch (Throwable e) {}
        Scheduler.scheduleAll(this);
        load(getIntent());
    }
    void load(Intent in){
        String p = in != null ? in.getStringExtra("p") : null;
        boolean t = in != null && in.getBooleanExtra("test", false);
        web.loadUrl("file:///android_asset/index.html" + (p != null ? "?p=" + p + (t ? "&test=1" : "") : ""));
    }
    @Override public void onRequestPermissionsResult(int rc, String[] p, int[] g){
        if (rc == 2 && pendingCall != null && g.length > 0 && g[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) startActivity(new Intent(Intent.ACTION_CALL, pendingCall));
        pendingCall = null;
    }
    @Override public boolean onKeyDown(int k, android.view.KeyEvent e){
        if (k == android.view.KeyEvent.KEYCODE_VOLUME_UP || k == android.view.KeyEvent.KEYCODE_VOLUME_DOWN || k == android.view.KeyEvent.KEYCODE_VOLUME_MUTE) return true;
        return super.onKeyDown(k, e);
    }
    @Override protected void onDestroy(){ try { if (tts != null) tts.shutdown(); } catch (Exception e) {} super.onDestroy(); }
    @Override protected void onNewIntent(Intent in){ super.onNewIntent(in); setIntent(in); load(in); }
}
