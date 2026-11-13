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
    int permStep = 0; boolean permBusy = false; long permT = 0;
    static final int GR = android.content.pm.PackageManager.PERMISSION_GRANTED;
    // Pede as permissões na abertura (o Android não permite pedir no ato de instalar): notificações, ligação, tela cheia e bateria
    void nextPerm(){
        try {
            if (permStep == 0) {
                permStep = 1;
                java.util.ArrayList<String> need = new java.util.ArrayList<>();
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != GR) need.add(Manifest.permission.POST_NOTIFICATIONS);
                if (checkSelfPermission(Manifest.permission.CALL_PHONE) != GR) need.add(Manifest.permission.CALL_PHONE);
                if (!need.isEmpty()) { permBusy = true; permT = System.currentTimeMillis(); requestPermissions(need.toArray(new String[0]), 3); return; }
            }
            if (permStep == 1) {
                permStep = 2;
                if (Build.VERSION.SDK_INT >= 34 && !((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).canUseFullScreenIntent()) {
                    permBusy = true; permT = System.currentTimeMillis();
                    startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName())));
                    return;
                }
            }
            if (permStep == 2) {
                permStep = 3;
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    permBusy = true; permT = System.currentTimeMillis();
                    startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
                }
            }
        } catch (Throwable e) {}
    }
    @Override protected void onResume(){
        super.onResume();
        if (permBusy && System.currentTimeMillis() - permT > 1000) { permBusy = false; nextPerm(); }
    }
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
        @JavascriptInterface public void speakSeq(String json){
            startService(new Intent(MainActivity.this, AlarmService.class).setAction("DUCK"));
            runOnUiThread(() -> {
                try {
                    if (tts == null || !ttsOk) { startService(new Intent(MainActivity.this, AlarmService.class).setAction("UNDUCK")); return; }
                    org.json.JSONArray a = new org.json.JSONArray(json);
                    for (int i = 0; i < a.length(); i++) {
                        org.json.JSONObject o = a.getJSONObject(i);
                        boolean last = i == a.length() - 1;
                        String id = last ? "last" : "p" + i;
                        int mode = i == 0 ? android.speech.tts.TextToSpeech.QUEUE_FLUSH : android.speech.tts.TextToSpeech.QUEUE_ADD;
                        if (o.has("p")) tts.playSilentUtterance(o.getLong("p"), mode, id);
                        else tts.speak(o.getString("t"), mode, null, id);
                    }
                } catch (Exception e) { startService(new Intent(MainActivity.this, AlarmService.class).setAction("UNDUCK")); }
            });
        }
        @JavascriptInterface public void silence(){
            runOnUiThread(() -> { try { if (tts != null) tts.stop(); } catch (Exception e) {} });
            startService(new Intent(MainActivity.this, AlarmService.class).setAction("UNDUCK"));
        }
        @JavascriptInterface public void mealAnswer(int m, boolean ok, int att, boolean testFlag){
            if (m < 0 || m >= Scheduler.MEAL_TIME.length) return;
            if (!testFlag) {
                if (ok) LogStore.addMeal(MainActivity.this, Scheduler.MEAL_TIME[m], Scheduler.MEAL_LOG[m], true);
                else if (att >= 2) LogStore.addMeal(MainActivity.this, Scheduler.MEAL_TIME[m], Scheduler.MEAL_LOG[m], false);
            }
            if (!ok && att < 2) Scheduler.mealQ(MainActivity.this, m, att + 1, testFlag ? 10000L : 25 * 60 * 1000L, testFlag);
            startService(new Intent(MainActivity.this, AlarmService.class).setAction("STOP"));
        }
        @JavascriptInterface public void setMeals(boolean on){
            LogStore.sp(MainActivity.this).edit().putBoolean("meals", on).apply();
            if (on) { for (int m = 0; m < Scheduler.MEAL_TIME.length; m++) { try { Scheduler.scheduleMeal(MainActivity.this, m); } catch (Throwable e) {} } }
            else Scheduler.cancelMeals(MainActivity.this);
        }
        @JavascriptInterface public int nextVoice(){
            try { return tts == null ? 0 : Voice.next(MainActivity.this, tts); } catch (Exception e) { return 0; }
        }
        @JavascriptInterface public boolean getMeals(){ return Scheduler.mealsOn(MainActivity.this); }
        @JavascriptInterface public void testMeal(int m){
            if (m < 0 || m >= Scheduler.MEAL_TIME.length) return;
            sendBroadcast(new Intent(MainActivity.this, AlarmReceiver.class).putExtra("meal", m).putExtra("stage", 0).putExtra("test", true));
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
        try { tts = new android.speech.tts.TextToSpeech(this, st -> { try { if (st == android.speech.tts.TextToSpeech.SUCCESS && tts != null) { Voice.apply(this, tts);
                tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener(){
                    public void onStart(String id){}
                    public void onDone(String id){ if (id != null && id.startsWith("p")) return; startService(new Intent(MainActivity.this, AlarmService.class).setAction("UNDUCK")); }
                    public void onError(String id){ startService(new Intent(MainActivity.this, AlarmService.class).setAction("UNDUCK")); }
                });
                ttsOk = true; } } catch (Throwable e) {} }); } catch (Throwable e) {}
        Scheduler.scheduleAll(this);
        load(getIntent());
        web.postDelayed(() -> nextPerm(), 800);
    }
    void load(Intent in){
        if (in != null && in.getBooleanExtra("stopAlarm", false)) startService(new Intent(this, AlarmService.class).setAction("STOP"));
        if (in != null && in.hasExtra("meal")) {
            web.loadUrl("file:///android_asset/index.html?meal=" + in.getIntExtra("meal", 0) + "&stage=" + in.getIntExtra("stage", 0) + "&att=" + in.getIntExtra("att", 1) + (in.getBooleanExtra("test", false) ? "&test=1" : ""));
            return;
        }
        String p = in != null ? in.getStringExtra("p") : null;
        boolean t = in != null && in.getBooleanExtra("test", false);
        web.loadUrl("file:///android_asset/index.html" + (p != null ? "?p=" + p + (t ? "&test=1" : "") : ""));
    }
    @Override public void onRequestPermissionsResult(int rc, String[] p, int[] g){
        if (rc == 3) { permBusy = false; nextPerm(); return; }
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
