package br.lourdinha;
import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import android.speech.tts.TextToSpeech;
import java.util.Locale;
public class AlarmService extends Service {
    static final String CH = "remedio_alarme";
    int idx = 0; boolean test = false, active = false;
    volatile boolean playing = false; volatile float vol = 1f; long lastDuck = 0; boolean snd = true;
    Thread audioThread; TextToSpeech tts; boolean ttsReady = false;
    Handler h = new Handler(Looper.getMainLooper());
    PowerManager.WakeLock wl;
    final Runnable speak = new Runnable(){ public void run(){ if(!active) return; say(); h.postDelayed(this, 9000);} };
    final Runnable timeout = new Runnable(){ public void run(){ if(active){ if(!test) LogStore.add(AlarmService.this, Scheduler.TIMES[idx], idx, null); finish(); } } };

    final Runnable keepVol = new Runnable(){ public void run(){
        if (!active) return;
        try { AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE); am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0); } catch (Exception e) {}
        h.postDelayed(this, 1500);
    } };

    @Override public IBinder onBind(Intent i){ return null; }

    @Override public int onStartCommand(Intent in, int flags, int startId){
        String act = in != null ? in.getAction() : null;
        if (in != null) { idx = in.getIntExtra("i", idx); test = in.getBooleanExtra("test", test); }
        if ("OK".equals(act)) { if (active && !test) { LogStore.add(this, Scheduler.TIMES[idx], idx, true); Scheduler.resetAtt(this); } finish(); return START_NOT_STICKY; }
        if ("NO".equals(act)) { if (active && !test) Scheduler.no(this, idx); finish(); return START_NOT_STICKY; }
        if ("HUSH".equals(act)) { if (!active) { stopSelf(); return START_NOT_STICKY; } playing = false; h.removeCallbacks(speak); try { if (tts != null) tts.stop(); } catch (Exception e) {} return START_NOT_STICKY; }
        if ("DUCK".equals(act)) { if (!active) { stopSelf(); return START_NOT_STICKY; } vol = 0.1f; lastDuck = System.currentTimeMillis(); h.removeCallbacks(speak); try { if (tts != null) tts.stop(); } catch (Exception e) {} return START_NOT_STICKY; }
        if ("UNDUCK".equals(act)) { if (!active) { stopSelf(); return START_NOT_STICKY; } vol = 1f; h.removeCallbacks(speak); h.postDelayed(speak, Math.max(1000L, 20000L - (System.currentTimeMillis() - lastDuck))); return START_NOT_STICKY; }
        if ("STOP".equals(act)) { finish(); return START_NOT_STICKY; }
        if (active) return START_NOT_STICKY;
        start();
        return START_NOT_STICKY;
    }

    void start(){
        active = true;
        createChannel();
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(1, n);
        try { PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE); wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lourdinha:alarm"); wl.acquire(21 * 60 * 1000L); } catch (Exception e) {}
        try { AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE); am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0); } catch (Exception e) {}
        try { Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE); v.vibrate(new long[]{0,600,400,600,400,600}, -1); } catch (Exception e) {}
        snd = LogStore.sp(this).getBoolean("snd", true);
        h.post(keepVol);
        if (snd) startAudio();
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && tts != null) {
                Voice.apply(tts);
                tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                ttsReady = true;
                h.post(speak);
            }
        });
        h.postDelayed(timeout, 20 * 60 * 1000L);
    }

    void say(){
        if (!snd || !ttsReady || tts == null) return;
        try {
            Bundle b = new Bundle();
            b.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM);
            tts.speak("Bora, Lourdinha! Tomar o remédio da bolsa " + Scheduler.NAMES[idx] + "!", TextToSpeech.QUEUE_FLUSH, b, "lourdinha");
        } catch (Exception e) {}
    }

    void startAudio(){
        playing = true;
        audioThread = new Thread(() -> {
            AudioTrack tr = null;
            try {
                final int sr = 22050;
                int[] notes = {523,659,784,659,587,698,880,698,523,659,784,1047,784,659,587,523};
                int per = (int)(sr * 0.5);
                short[] buf = new short[per];
                int minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                tr = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(Math.max(minBuf, per * 2))
                    .setTransferMode(AudioTrack.MODE_STREAM).build();
                tr.setVolume(1.0f);
                tr.play();
                int k = 0;
                while (playing) {
                    tr.setVolume(vol);
                    double f = notes[k % notes.length];
                    for (int s = 0; s < per; s++) {
                        double t = (double) s / sr;
                        double env = Math.exp(-4.0 * t);
                        double sq = Math.signum(Math.sin(2 * Math.PI * f * t));
                        double v = (0.6 * Math.sin(2 * Math.PI * f * t) + 0.4 * sq) * env;
                        buf[s] = (short) (v * 30000);
                    }
                    tr.write(buf, 0, per);
                    k++;
                }
            } catch (Exception e) {
            } finally {
                try { if (tr != null) { tr.stop(); tr.release(); } } catch (Exception e) {}
            }
        });
        audioThread.start();
    }

    void finish(){
        active = false; playing = false;
        h.removeCallbacksAndMessages(null);
        try { if (tts != null) { tts.stop(); tts.shutdown(); tts = null; } } catch (Exception e) {}
        try { if (wl != null && wl.isHeld()) wl.release(); } catch (Exception e) {}
        try { stopForeground(true); } catch (Exception e) {}
        stopSelf();
    }

    @Override public void onDestroy(){ playing = false; super.onDestroy(); }

    void createChannel(){
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH, "Alarme de remédios", NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(ch);
        }
    }

    PendingIntent svc(String action, int code){
        Intent i = new Intent(this, AlarmService.class).setAction(action).putExtra("i", idx).putExtra("test", test);
        return PendingIntent.getService(this, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    Notification buildNotification(){
        Intent open = new Intent(this, MainActivity.class).putExtra("p", Scheduler.TIMES[idx]).putExtra("test", test).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 300 + idx, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
         .setContentTitle("Hora do remédio! Bolsa " + Scheduler.NAMES[idx].toUpperCase())
         .setContentText("Toque para ver os remédios")
         .setContentIntent(pi).setFullScreenIntent(pi, true)
         .setColor(Scheduler.COLORS[idx]).setColorized(true)
         .setCategory(Notification.CATEGORY_ALARM).setOngoing(true)
         .addAction(android.R.drawable.checkbox_on_background, "Tomei tudo", svc("OK", 1))
         .addAction(android.R.drawable.ic_delete, "Não consegui", svc("NO", 2));
        return b.build();
    }
}
