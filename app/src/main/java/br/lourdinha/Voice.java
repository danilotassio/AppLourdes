package br.lourdinha;
import android.speech.tts.*;
import java.util.*;
public class Voice {
    public static void apply(TextToSpeech t){
        try {
            t.setLanguage(new Locale("pt","BR"));
            for (android.speech.tts.Voice v : t.getVoices()) {
                String n = v.getName().toLowerCase();
                if (v.getLocale().getLanguage().equals("pt") && v.getLocale().getCountry().equals("BR") && !v.isNetworkConnectionRequired()) {
                    if (n.contains("ptd") || n.contains("male") && !n.contains("female")) { t.setVoice(v); break; }
                }
            }
        } catch (Exception e) {}
        try { t.setPitch(0.8f); t.setSpeechRate(0.92f); } catch (Exception e) {}
    }
}
