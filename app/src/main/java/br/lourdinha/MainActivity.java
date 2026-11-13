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
    class Bridge {
        @JavascriptInterface public String answer(boolean ok, String time){
            int idx = java.util.Arrays.asList(Scheduler.TIMES).indexOf(time);
            String r = "ok";
            if (idx >= 0) {
                if (ok) { LogStore.add(MainActivity.this, time, idx, true); Scheduler.resetAtt(MainActivity.this); }
                else r = Scheduler.no(MainActivity.this, idx);
            }
            startService(new Intent(MainActivity.this, AlarmService.class).setAction("STOP"));
            return r;
        }
        @JavascriptInterface public void hush(){ startService(new Intent(MainActivity.this, AlarmService.class).setAction("HUSH")); }
        @JavascriptInterface public void speak(String t){
            runOnUiThread(() -> { if (tts != null && ttsOk) tts.speak(t, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "u"); });
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
        try { tts = new android.speech.tts.TextToSpeech(this, st -> { try { if (st == android.speech.tts.TextToSpeech.SUCCESS && tts != null) { Voice.apply(tts); ttsOk = true; } } catch (Throwable e) {} }); } catch (Throwable e) {}
        Scheduler.scheduleAll(this);
        load(getIntent());
    }
    void load(Intent in){
        String p = in != null ? in.getStringExtra("p") : null;
        boolean t = in != null && in.getBooleanExtra("test", false);
        web.loadUrl("file:///android_asset/index.html" + (p != null ? "?p=" + p + (t ? "&test=1" : "") : ""));
    }
    @Override protected void onDestroy(){ try { if (tts != null) tts.shutdown(); } catch (Exception e) {} super.onDestroy(); }
    @Override protected void onNewIntent(Intent in){ super.onNewIntent(in); setIntent(in); load(in); }
}
