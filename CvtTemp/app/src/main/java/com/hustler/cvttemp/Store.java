package com.hustler.cvttemp;

import android.content.Context;
import android.content.SharedPreferences;

/** Данные для виджета рабочего стола. */
public final class Store {
    private static final String PREF = "cvt_temp_widget";
    public static final float NONE = -999f;

    private Store() {}

    public static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void save(Context c, float cvt, float eng, String cz, String ez, String mode) {
        prefs(c).edit()
                .putFloat("cvt", cvt)
                .putFloat("eng", eng)
                .putString("cz", cz == null ? "" : cz)
                .putString("ez", ez == null ? "" : ez)
                .putString("mode", mode == null ? "" : mode)
                .putLong("ts", System.currentTimeMillis())
                .apply();
    }

    public static int zoneColor(String zone) {
        if (zone == null) return 0xFF93A7C9;
        switch (zone) {
            case "ХОЛОДНЫЙ": return 0xFF3FD9FF;
            case "НОРМА":    return 0xFF57E389;
            case "ГОРЯЧО":   return 0xFFFFB020;
            case "ПЕРЕГРЕВ": return 0xFFFF4040;
            default:         return 0xFF93A7C9;
        }
    }

    public static String tempText(float v) {
        return v <= NONE + 1 ? "--°" : Math.round(v) + "°";
    }

    public static int percent(float v, int min, int max) {
        if (v <= NONE + 1) return 0;
        float p = (v - min) / (float) (max - min) * 100f;
        return Math.max(0, Math.min(100, Math.round(p)));
    }
}
