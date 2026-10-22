package sg.psi.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

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

        // The widget shows the first two locations in the app's card order.
        List<Location> locations = Location.ordered(prefs);
        Integer home = bind(views, result, locations.get(0),
                R.id.home_label, R.id.home_value, R.id.home_band);
        Integer office = null;
        boolean two = locations.size() > 1;
        views.setViewVisibility(R.id.divider, two ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.office_column, two ? View.VISIBLE : View.GONE);
        if (two) {
            office = bind(views, result, locations.get(1),
                    R.id.office_label, R.id.office_value, R.id.office_band);
        }

        // Like the app, the widget takes on the colour of the worse reading.
        Integer worst = home;
        if (worst == null || (office != null && office > worst)) worst = office;
        views.setInt(R.id.widget_root, "setBackgroundResource", PsiClient.widgetBackground(worst));

        String time = PsiClient.shortTime(result == null ? null : result.timestamp);
        views.setTextViewText(R.id.widget_updated,
                time.isEmpty() ? "24-hr PSI" : "24-hr PSI · " + time);

        Intent open = new Intent(context, MainActivity.class);
        PendingIntent tap = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, tap);

        manager.updateAppWidget(ids, views);
    }

    private static Integer bind(RemoteViews views, PsiClient.Result result, Location location,
                                int labelId, int valueId, int bandId) {
        views.setTextViewText(labelId, location.icon + " " + location.name + " · " + PsiClient.label(location.region));
        Integer psi = result == null ? null : result.readings.get(location.region);
        views.setTextViewText(valueId, psi == null ? "–" : String.valueOf(psi));
        views.setTextViewText(bandId, psi == null ? "" : PsiClient.bandName(psi));
        return psi;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PsiClient.PREFS, Context.MODE_PRIVATE);
    }
}
