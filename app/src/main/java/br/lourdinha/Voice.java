package br.lourdinha;
import android.content.Context;
import android.speech.tts.*;
import java.util.*;
public class Voice {
    /** Vozes pt-BR do aparelho, da melhor qualidade para a pior (offline primeiro). */
    public static List<android.speech.tts.Voice> list(TextToSpeech t){
        List<android.speech.tts.Voice> r = new ArrayList<>();
        try {
            for (android.speech.tts.Voice v : t.getVoices())
                if (v.getLocale().getLanguage().equals("pt") && v.getLocale().getCountry().equals("BR")) r.add(v);
        } catch (Exception e) {}
        Collections.sort(r, (a, b) -> {
            int c = Integer.compare(a.isNetworkConnectionRequired() ? 1 : 0, b.isNetworkConnectionRequired() ? 1 : 0);
            if (c != 0) return c;
            c = Integer.compare(b.getQuality(), a.getQuality());
            return c != 0 ? c : a.getName().compareTo(b.getName());
        });
        return r;
    }
    public static void apply(Context c, TextToSpeech t){
        try {
            t.setLanguage(new Locale("pt","BR"));
            List<android.speech.tts.Voice> l = list(t);
            String saved = LogStore.sp(c).getString("voice", "");
            android.speech.tts.Voice pick = l.isEmpty() ? null : l.get(0);
            for (android.speech.tts.Voice v : l) if (v.getName().equals(saved)) { pick = v; break; }
            if (pick != null) t.setVoice(pick);
        } catch (Exception e) {}
        try { t.setPitch(1.0f); t.setSpeechRate(0.9f); } catch (Exception e) {}
    }
    /** Passa para a próxima voz; devolve o número dela (1..n) ou 0 se não há voz. */
    public static int next(Context c, TextToSpeech t){
        List<android.speech.tts.Voice> l = list(t);
        if (l.isEmpty()) return 0;
        String cur = "";
        try { android.speech.tts.Voice v = t.getVoice(); if (v != null) cur = v.getName(); } catch (Exception e) {}
        int i = 0;
        for (int k = 0; k < l.size(); k++) if (l.get(k).getName().equals(cur)) { i = (k + 1) % l.size(); break; }
        LogStore.sp(c).edit().putString("voice", l.get(i).getName()).apply();
        try { t.setVoice(l.get(i)); } catch (Exception e) {}
        return i + 1;
    }
}
