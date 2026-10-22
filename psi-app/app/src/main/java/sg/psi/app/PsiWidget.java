package sg.psi.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

/** Home-screen widget showing the 24-hr PSI for the Home and Office regions. */
public class PsiWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        Context app = context.getApplicationContext();
        updateAll(app);

        // Network calls are not allowed on the main thread; keep the broadcast alive while we fetch.
        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                PsiClient.Result result = PsiClient.fetchLatest();
                PsiClient.save(prefs(app), result);
                updateAll(app);
            } catch (Exception ignored) {
                // Keep showing the last cached reading.
            } finally {
                pending.finish();
            }
        }).start();
    }

    /** Redraws every widget instance from the cached reading and saved regions. */
    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, PsiWidget.class));
        if (ids.length == 0) return;

        SharedPreferences prefs = prefs(context);
        PsiClient.Result result = PsiClient.load(prefs);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_psi);

        bind(views, result, "Home", prefs.getString(PsiClient.PREF_HOME, PsiClient.DEFAULT_HOME),
                R.id.home_label, R.id.home_value, R.id.home_band);
        bind(views, result, "Office", prefs.getString(PsiClient.PREF_OFFICE, PsiClient.DEFAULT_OFFICE),
                R.id.office_label, R.id.office_value, R.id.office_band);

        String ts = result == null ? "" : result.timestamp;
        views.setTextViewText(R.id.widget_updated,
                ts.length() >= 16 ? "24-hr PSI · " + ts.substring(11, 16) : "24-hr PSI");

        Intent open = new Intent(context, MainActivity.class);
        PendingIntent tap = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, tap);

        manager.updateAppWidget(ids, views);
    }

    private static void bind(RemoteViews views, PsiClient.Result result, String name, String region,
                             int labelId, int valueId, int bandId) {
        views.setTextViewText(labelId, name + " · " + PsiClient.label(region));
        Integer psi = result == null ? null : result.readings.get(region);
        if (psi == null) {
            views.setTextViewText(valueId, "–");
            views.setTextColor(valueId, 0xFF111111);
            views.setTextViewText(bandId, "");
            return;
        }
        views.setTextViewText(valueId, String.valueOf(psi));
        views.setTextColor(valueId, PsiClient.bandColor(psi));
        views.setTextViewText(bandId, PsiClient.bandName(psi));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PsiClient.PREFS, Context.MODE_PRIVATE);
    }
}
