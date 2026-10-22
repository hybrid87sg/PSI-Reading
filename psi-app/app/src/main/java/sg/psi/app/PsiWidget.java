package sg.psi.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.RemoteViews;

import java.util.List;

/**
 * Resizable home-screen widget showing the 24-hr PSI for the user's locations, in the
 * app's card order. It shows as many locations as fit its current size.
 */
public class PsiWidget extends AppWidgetProvider {

    /** Rough space one location needs; used to work out how many fit. */
    private static final int CELL_WIDTH_DP = 120;
    private static final int CELL_HEIGHT_DP = 70;
    /** Room taken by the padding and the "24-hr PSI" line. */
    private static final int HEADER_HEIGHT_DP = 46;
    private static final int MAX_COLUMNS = 4;
    private static final int MAX_ROWS = 3;

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

    /** Called when the user resizes the widget. */
    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        updateAll(context.getApplicationContext());
    }

    /** Redraws every widget instance from the cached reading and saved locations. */
    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, PsiWidget.class));
        if (ids.length == 0) return;

        SharedPreferences prefs = prefs(context);
        PsiClient.Result result = PsiClient.load(prefs);
        List<Location> locations = Location.ordered(prefs);
        for (int id : ids) {
            manager.updateAppWidget(id, build(context, manager.getAppWidgetOptions(id), result, locations));
        }
    }

    private static RemoteViews build(Context context, Bundle options, PsiClient.Result result,
                                     List<Location> locations) {
        // In portrait the widget is its minimum width by its maximum height.
        int widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250);
        int heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110);
        if (widthDp <= 0) widthDp = 250;
        if (heightDp <= 0) heightDp = 110;

        int columns = clamp(widthDp / CELL_WIDTH_DP, 1, MAX_COLUMNS);
        int rows = clamp((heightDp - HEADER_HEIGHT_DP) / CELL_HEIGHT_DP, 1, MAX_ROWS);
        int count = Math.min(columns * rows, locations.size());
        // Don't leave whole empty rows or columns when there are fewer locations than slots.
        rows = (count + columns - 1) / columns;
        if (rows == 1) columns = count;
        boolean crowded = rows >= 3 || columns >= 4;

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_psi);
        views.removeAllViews(R.id.widget_grid);
        Integer worst = null;
        for (int r = 0; r < rows; r++) {
            RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_row);
            for (int c = 0; c < columns; c++) {
                int i = r * columns + c;
                RemoteViews cell = new RemoteViews(context.getPackageName(), R.layout.widget_cell);
                if (i < count) {
                    Location location = locations.get(i);
                    Integer psi = result == null ? null : result.readings.get(location.region);
                    if (psi != null && (worst == null || psi > worst)) worst = psi;
                    cell.setTextViewText(R.id.cell_label, location.icon + " " + location.name);
                    cell.setTextViewText(R.id.cell_value, psi == null ? "–" : String.valueOf(psi));
                    cell.setTextViewTextSize(R.id.cell_value, TypedValue.COMPLEX_UNIT_SP, crowded ? 26 : 34);
                    cell.setTextViewText(R.id.cell_band, (psi == null ? "" : PsiClient.bandName(psi) + " · ")
                            + PsiClient.label(location.region));
                }
                // Cells past the last location stay empty so the others keep equal widths.
                row.addView(R.id.widget_row, cell);
            }
            views.addView(R.id.widget_grid, row);
        }

        // Like the app, the widget takes on the colour of the worst reading it shows.
        views.setInt(R.id.widget_root, "setBackgroundResource", PsiClient.widgetBackground(worst));
        String time = PsiClient.shortTime(result == null ? null : result.timestamp);
        views.setTextViewText(R.id.widget_updated, time.isEmpty() ? "24-hr PSI" : "24-hr PSI · " + time);

        Intent open = new Intent(context, MainActivity.class);
        PendingIntent tap = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, tap);
        return views;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PsiClient.PREFS, Context.MODE_PRIVATE);
    }
}
