package com.hustler.cvttemp;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Виджет рабочего стола: две температуры, линейные шкалы, зоны. */
public class TempWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) updateOne(context, manager, id);
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (manager == null) return;
        ComponentName cn = new ComponentName(context, TempWidget.class);
        int[] ids = manager.getAppWidgetIds(cn);
        for (int id : ids) updateOne(context, manager, id);
    }

    private static void updateOne(Context c, AppWidgetManager manager, int id) {
        SharedPreferences p = Store.prefs(c);
        float cvt = p.getFloat("cvt", Store.NONE);
        float eng = p.getFloat("eng", Store.NONE);
        String cz = p.getString("cz", "");
        String ez = p.getString("ez", "");
        String mode = p.getString("mode", "НЕТ ДАННЫХ");
        long ts = p.getLong("ts", 0);

        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_temp);

        v.setTextViewText(R.id.w_cvt_temp, Store.tempText(cvt));
        v.setTextColor(R.id.w_cvt_temp, Store.zoneColor(cz));
        v.setTextViewText(R.id.w_cvt_zone, cz == null || cz.isEmpty() ? "—" : cz);
        v.setTextColor(R.id.w_cvt_zone, Store.zoneColor(cz));
        v.setProgressBar(R.id.w_cvt_bar, 100, Store.percent(cvt, 0, 150), false);

        v.setTextViewText(R.id.w_eng_temp, Store.tempText(eng));
        v.setTextColor(R.id.w_eng_temp, Store.zoneColor(ez));
        v.setTextViewText(R.id.w_eng_zone, ez == null || ez.isEmpty() ? "—" : ez);
        v.setTextColor(R.id.w_eng_zone, Store.zoneColor(ez));
        v.setProgressBar(R.id.w_eng_bar, 100, Store.percent(eng, 40, 130), false);

        v.setTextViewText(R.id.w_mode, mode);
        v.setTextViewText(R.id.w_time, ts == 0 ? "ожидание данных"
                : "обновлено " + new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(ts)));

        Intent open = new Intent(c, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(c, 10, open, flags);
        v.setOnClickPendingIntent(R.id.widget_root, pi);

        manager.updateAppWidget(id, v);
    }
}
