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
    ValueCallback<Uri[]> fcb;
    String pendingBackup = null;
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
                if (checkSelfPermission(Manifest.permission.SEND_SMS) != GR) need.add(Manifest.permission.SEND_SMS);
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
                if (ok) LogStore.addMeal(MainActivity.this, Scheduler.mealTime(MainActivity.this, m), Scheduler.MEAL_LOG[m], true);
                else if (att >= 2) LogStore.addMeal(MainActivity.this, Scheduler.mealTime(MainActivity.this, m), Scheduler.MEAL_LOG[m], false);
            }
            if (!ok && att < 2) Scheduler.mealQ(MainActivity.this, m, att + 1, testFlag ? 10000L : 25 * 60 * 1000L, testFlag);
            startService(new Intent(MainActivity.this, AlarmService.class).setAction("STOP"));
        }
        @JavascriptInterface public void setRemind(boolean on){
            LogStore.sp(MainActivity.this).edit().putBoolean("remind", on).apply();
            if (on) Scheduler.scheduleAll(MainActivity.this);
        }
        @JavascriptInterface public boolean getRemind(){ return LogStore.sp(MainActivity.this).getBoolean("remind", false); }
        @JavascriptInterface public void setMeals(boolean on){
            LogStore.sp(MainActivity.this).edit().putBoolean("meals", on).apply();
            if (on) { for (int m = 0; m < Scheduler.MEAL_TIME.length; m++) { try { Scheduler.scheduleMeal(MainActivity.this, m); } catch (Throwable e) {} } }
            else Scheduler.cancelMeals(MainActivity.this);
        }
        @JavascriptInterface public void setMealTime(int m, String hhmm){
            if (m < 0 || m >= Scheduler.MEAL_TIME.length || hhmm == null || !hhmm.matches("\\d\\d:\\d\\d")) return;
            LogStore.sp(MainActivity.this).edit().putString("mt" + m, hhmm).apply();
            if (Scheduler.mealsOn(MainActivity.this)) { try { Scheduler.scheduleMeal(MainActivity.this, m); } catch (Throwable e) {} }
        }
        @JavascriptInterface public String getMealTimes(){
            return Scheduler.mealTime(MainActivity.this, 0) + "," + Scheduler.mealTime(MainActivity.this, 1) + "," + Scheduler.mealTime(MainActivity.this, 2);
        }
        @JavascriptInterface public void listen(){
            runOnUiThread(() -> {
                try {
                    Intent i = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
                    i.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Fale a pergunta para a médica");
                    startActivityForResult(i, 77);
                } catch (Throwable e) { web.evaluateJavascript("window.onVozErr&&onVozErr()", null); }
            });
        }
        @JavascriptInterface public void setAppt(String d, String t){
            if (d == null || t == null || !d.matches("\\d{4}-\\d\\d-\\d\\d") || !t.matches("\\d\\d:\\d\\d")) return;
            LogStore.sp(MainActivity.this).edit().putString("appt", d).putString("apptT", t).apply();
            try { Scheduler.scheduleReminders(MainActivity.this); } catch (Throwable e) {}
        }
        @JavascriptInterface public void setSms(boolean on, String extras){
            LogStore.sp(MainActivity.this).edit().putBoolean("sms", on).putString("smsx", extras == null ? "" : extras.replaceAll("[^0-9,]", "")).apply();
            if (on && checkSelfPermission(Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.SEND_SMS}, 5));
        }
        @JavascriptInterface public void notifySms(String msg){
            try { if (msg != null && msg.length() > 0 && Sms.on(MainActivity.this)) Sms.send(MainActivity.this, msg); } catch (Throwable e) {}
        }
        @JavascriptInterface public String getMode(){ return Push.mode(MainActivity.this); }
        @JavascriptInterface public void setMode(String m){
            if (!"lourdinha".equals(m) && !"cuidador".equals(m)) return;
            android.content.SharedPreferences.Editor e = LogStore.sp(MainActivity.this).edit().putString("mode", m);
            if ("lourdinha".equals(m) && !Push.valid(Push.code(MainActivity.this))) e.putString("pair", Push.newCode());
            e.apply(); updateIcon();
            if ("cuidador".equals(m)) runOnUiThread(() -> { java.util.ArrayList<String> pm = new java.util.ArrayList<>(); if (Build.VERSION.SDK_INT >= 33) pm.add(Manifest.permission.POST_NOTIFICATIONS); pm.add(Manifest.permission.SEND_SMS); pm.add(Manifest.permission.ACCESS_FINE_LOCATION); requestPermissions(pm.toArray(new String[0]), 1); });
            else Scheduler.scheduleAll(MainActivity.this);
        }
        @JavascriptInterface public String getPairCode(){ return Push.code(MainActivity.this); }
        @JavascriptInterface public String setPairCode(String c){
            c = c == null ? "" : c.trim();
            if (!Push.valid(c)) return "erro";
            LogStore.sp(MainActivity.this).edit().putString("pair", c).apply();
            Push.register(MainActivity.this);
            return "ok";
        }
        @JavascriptInterface public String getAlerts(){ return LogStore.sp(MainActivity.this).getString("alerts", "[]"); }
        @JavascriptInterface public String takeNew(){
            String n = LogStore.sp(MainActivity.this).getString("alertNew", "");
            if (!n.isEmpty()) LogStore.sp(MainActivity.this).edit().remove("alertNew").apply();
            return n;
        }
        @JavascriptInterface public void openUrl(String u){
            try { if (u != null && u.startsWith("https://maps.google.com/")) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Throwable e) {}
        }
        @JavascriptInterface public void setName(String n){ LogStore.sp(MainActivity.this).edit().putString("cname", n == null ? "" : n.trim()).apply(); Push.profile(MainActivity.this, false); updateIcon(); }
        @JavascriptInterface public void sendProfile(){ Push.profile(MainActivity.this, false); }
        @JavascriptInterface public String getMyId(){ String t = LogStore.sp(MainActivity.this).getString("ftok", ""); return t.length() >= 12 ? t.substring(t.length() - 12) : ""; }
        @JavascriptInterface public String getSrv(){ return LogStore.sp(MainActivity.this).getString("srv", ""); }
        @JavascriptInterface public String getProfs(){ return LogStore.sp(MainActivity.this).getString("profs", "[]"); }
        @JavascriptInterface public void fetchProfs(){ Push.profs(MainActivity.this, () -> runOnUiThread(() -> web.evaluateJavascript("window.onProfs&&onProfs()", null))); }
        @JavascriptInterface public void askLoc(String id){ Push.locReq(MainActivity.this, id); }
        @JavascriptInterface public void careCall(String nome, String ref, String txt){ Push.care(MainActivity.this, nome, ref, txt); }
        @JavascriptInterface public void notifyCare(String msg){ try { Push.send(MainActivity.this, msg); } catch (Throwable e) {} }
        @JavascriptInterface public void fetchCfg(){ new Thread(() -> Push.cfgFetchNow(MainActivity.this)).start(); }
        @JavascriptInterface public String fetchCfgNow(){ Push.cfgFetchNow(MainActivity.this); return LogStore.sp(MainActivity.this).getString("cfg", "{}"); }
        @JavascriptInterface public String getCfg(){ return LogStore.sp(MainActivity.this).getString("cfg", "{}"); }
        @JavascriptInterface public void setCfg(String json){
            try { new org.json.JSONObject(json); LogStore.sp(MainActivity.this).edit().putString("cfg", json).apply(); Push.cfgSet(MainActivity.this, json); } catch (Throwable e) {}
        }
        @JavascriptInterface public void pushState(String json){ Push.state(MainActivity.this, json); }
        @JavascriptInterface public String getState(){ return LogStore.sp(MainActivity.this).getString("state", "{}"); }
        @JavascriptInterface public void refreshState(){ Push.fetch(MainActivity.this, () -> runOnUiThread(() -> web.evaluateJavascript("window.onState&&onState()", null))); }
        @JavascriptInterface public void setFilter(String json){ try { new org.json.JSONArray(json); LogStore.sp(MainActivity.this).edit().putString("cflt", json).apply(); } catch (Throwable e) {} }
        @JavascriptInterface public void clearAlerts(){ LogStore.sp(MainActivity.this).edit().remove("alerts").remove("alertNew").apply(); }
        @JavascriptInterface public void notifyFixed(String msg){
            try { if (msg != null && msg.length() > 0) Sms.sendFixed(MainActivity.this, msg); } catch (Throwable e) {}
        }
        @JavascriptInterface public void setBuy(String txt){
            try { LogStore.sp(MainActivity.this).edit().putString("buy", txt == null ? "" : txt).apply(); } catch (Throwable e) {}
        }
        @JavascriptInterface public void symptom(String k){
            try {
                if (k == null || !k.matches("[a-z]{2,10}")) return;
                org.json.JSONArray a = new org.json.JSONArray(LogStore.sp(MainActivity.this).getString("sintn", "[]"));
                a.put(new org.json.JSONObject().put("ts", System.currentTimeMillis()).put("k", k));
                org.json.JSONArray b = new org.json.JSONArray();
                for (int i = Math.max(0, a.length() - 500); i < a.length(); i++) b.put(a.get(i));
                LogStore.sp(MainActivity.this).edit().putString("sintn", b.toString()).apply();
                final String kk = k;
                new Thread(() -> {
                    try {
                        String hora = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new java.util.Date());
                        String nome = kk.equals("enjoo") ? "ENJOO" : kk.equals("dor") ? "DOR" : kk.equals("tontura") ? "TONTURA" : "";
                        String loc = Loc.battery(MainActivity.this) + Loc.link(MainActivity.this);
                        if (kk.equals("bem") && Sms.on(MainActivity.this)) Sms.send(MainActivity.this, "AppLourdinha: a Lourdinha registrou que esta BEM agora (" + hora + ")." + loc);
                        if (!nome.isEmpty() && Sms.on(MainActivity.this)) Sms.send(MainActivity.this, "AppLourdinha: a Lourdinha registrou " + nome + " agora (" + hora + "). Por favor, entre em contato." + loc);
                    } catch (Throwable e) {}
                }).start();
            } catch (Throwable e) {}
        }
        @JavascriptInterface public void setLife(int h){ LogStore.sp(MainActivity.this).edit().putInt("lifeH", Math.max(0, Math.min(72, h))).apply(); Watch.touch(MainActivity.this); }
        @JavascriptInterface public String testSms(){
            int n = Sms.send(MainActivity.this, "Teste do AppLourdinha: o aviso por SMS esta funcionando.");
            if (n < 0) { runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.SEND_SMS}, 5)); return "perm"; }
            return "ok:" + n;
        }
        @JavascriptInterface public void setAct(boolean on, String t0, String t1){
            SharedPreferences.Editor ed = LogStore.sp(MainActivity.this).edit().putBoolean("act", on);
            if (t0 != null && t0.matches("\\d\\d:\\d\\d")) ed.putString("act0", t0);
            if (t1 != null && t1.matches("\\d\\d:\\d\\d")) ed.putString("act1", t1);
            ed.apply();
            try { Scheduler.scheduleReminders(MainActivity.this); } catch (Throwable e) {}
        }
        @JavascriptInterface public String getPrefs(){
            try { org.json.JSONObject o = new org.json.JSONObject(); for (java.util.Map.Entry<String, ?> e : LogStore.sp(MainActivity.this).getAll().entrySet()) o.put(e.getKey(), e.getValue()); return o.toString(); } catch (Exception e) { return "{}"; }
        }
        @JavascriptInterface public void restorePrefs(String json){
            try {
                org.json.JSONObject o = new org.json.JSONObject(json);
                SharedPreferences.Editor ed = LogStore.sp(MainActivity.this).edit().clear();
                java.util.Iterator<String> it = o.keys();
                while (it.hasNext()) { String k = it.next(); Object v = o.get(k);
                    if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v); else if (v instanceof Long || (v instanceof Number && ((Number) v).longValue() > Integer.MAX_VALUE)) ed.putLong(k, ((Number) v).longValue()); else if (v instanceof Number) ed.putInt(k, ((Number) v).intValue()); else ed.putString(k, String.valueOf(v)); }
                ed.putLong("seen", System.currentTimeMillis());
                ed.apply();
                Scheduler.scheduleAll(MainActivity.this);
            } catch (Exception e) {}
        }
        @JavascriptInterface public void exportBackup(String name, String text){
            pendingBackup = text;
            runOnUiThread(() -> { try {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE, name);
                startActivityForResult(i, 79);
            } catch (Throwable e) { web.evaluateJavascript("window.onBackupSaved&&onBackupSaved(false)", null); } });
        }
        @JavascriptInterface public void pickBackup(){
            runOnUiThread(() -> { try {
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 78);
            } catch (Throwable e) { web.evaluateJavascript("window.onBackup&&onBackup('')", null); } });
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
        updateIcon();
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
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams fp){
                if (fcb != null) { try { fcb.onReceiveValue(null); } catch (Exception e) {} }
                fcb = cb;
                try { startActivityForResult(fp.createIntent(), 77); return true; }
                catch (Exception e) { fcb = null; return false; }
            }
        });
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
        Push.profile(this, false);
        Push.cfgFetch(this, () -> runOnUiThread(() -> { if (Loc.on(MainActivity.this) && !Loc.allowed(MainActivity.this)) requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 6); }));
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
    @Override protected void onActivityResult(int rc, int res, Intent data){
        super.onActivityResult(rc, res, data);
        if (rc == 77) { if (fcb != null) { try { fcb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data)); } catch (Exception e) { try { fcb.onReceiveValue(null); } catch (Exception e2) {} } fcb = null; } return; }
        if (rc == 79) {
            boolean ok = false;
            try { if (res == RESULT_OK && data != null && data.getData() != null && pendingBackup != null) { java.io.OutputStream os = getContentResolver().openOutputStream(data.getData()); os.write(pendingBackup.getBytes("UTF-8")); os.close(); ok = true; } } catch (Throwable e) {}
            pendingBackup = null;
            web.evaluateJavascript("window.onBackupSaved&&onBackupSaved(" + ok + ")", null);
            return;
        }
        if (rc == 78) {
            String txt = "";
            try { if (res == RESULT_OK && data != null && data.getData() != null) { java.io.InputStream is = getContentResolver().openInputStream(data.getData()); java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(); byte[] b = new byte[8192]; int n; while ((n = is.read(b)) > 0) bo.write(b, 0, n); is.close(); txt = bo.toString("UTF-8"); } } catch (Throwable e) {}
            web.evaluateJavascript("window.onBackup&&onBackup(" + org.json.JSONObject.quote(txt) + ")", null);
            return;
        }
        if (rc != 77) return;
        String t = "";
        if (res == RESULT_OK && data != null) {
            java.util.ArrayList<String> l = data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
            if (l != null && !l.isEmpty()) t = l.get(0);
        }
        String js = org.json.JSONObject.quote(t);
        web.evaluateJavascript("window.onVoz&&onVoz(" + js + ")", null);
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
    long lastTouch = 0;
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent ev){
        try { long n = System.currentTimeMillis(); if (ev.getAction() == android.view.MotionEvent.ACTION_DOWN && n - lastTouch > 30000) { lastTouch = n; Watch.touch(this); } } catch (Throwable e) {}
        return super.dispatchTouchEvent(ev);
    }
    @Override protected void onNewIntent(Intent in){ super.onNewIntent(in); setIntent(in); load(in); }

    /** Ícone só com a foto da Lourdinha quando o cuidador não é Anne Amorim nem Danilo Tássio. */
    void updateIcon(){
        try {
            String n = LogStore.sp(this).getString("cname", "").trim();
            String f = java.text.Normalizer.normalize(n, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
            boolean solo = !n.isEmpty() && "cuidador".equals(Push.mode(this)) && !f.equals("anne amorim") && !f.equals("danilo tassio");
            android.content.pm.PackageManager pm = getPackageManager();
            android.content.ComponentName pad = new android.content.ComponentName(this, "br.lourdinha.IconPadrao");
            android.content.ComponentName sol = new android.content.ComponentName(this, "br.lourdinha.IconSolo");
            int on = android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, off = android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            if (pm.getComponentEnabledSetting(sol) == (solo ? on : off) && pm.getComponentEnabledSetting(pad) == (solo ? off : on)) return;
            pm.setComponentEnabledSetting(solo ? sol : pad, on, android.content.pm.PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(solo ? pad : sol, off, android.content.pm.PackageManager.DONT_KILL_APP);
        } catch (Throwable e) {}
    }
}
