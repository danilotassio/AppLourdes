package br.lourdinha;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.WindowManager;
import android.webkit.*;
public class MainActivity extends Activity {
    WebView web;
    @Override protected void onCreate(Bundle s){
        super.onCreate(s);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true); ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        web.setWebChromeClient(new WebChromeClient());
        setContentView(web);
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (!am.canScheduleExactAlarms()) { try { startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))); } catch (Exception e) {} }
        }
        Scheduler.scheduleAll(this);
        load(getIntent());
    }
    void load(Intent in){
        String p = in != null ? in.getStringExtra("p") : null;
        if (p != null) {
            AudioManager a = (AudioManager) getSystemService(AUDIO_SERVICE);
            a.setStreamVolume(AudioManager.STREAM_MUSIC, a.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
        }
        web.loadUrl("file:///android_asset/index.html" + (p != null ? "?p=" + p : ""));
    }
    @Override protected void onNewIntent(Intent in){ super.onNewIntent(in); setIntent(in); load(in); }
}
