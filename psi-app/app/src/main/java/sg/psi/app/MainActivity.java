package sg.psi.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private View root;
    private View refresh;
    private TextView updated;
    private LinearLayout regionsRow;
    private Card home;
    private Card office;
    private Map<String, Integer> latest = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PsiClient.PREFS, MODE_PRIVATE);
        root = findViewById(R.id.root);
        refresh = findViewById(R.id.refresh);
        updated = findViewById(R.id.updated);
        regionsRow = findViewById(R.id.regions);
        ((TextView) findViewById(R.id.greeting)).setText(greeting());
        ((TextView) findViewById(R.id.date)).setText(
                new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date()));

        home = new Card(findViewById(R.id.home_card), "🏠", "Home",
                PsiClient.PREF_HOME, PsiClient.DEFAULT_HOME);
        office = new Card(findViewById(R.id.office_card), "🏢", "Office",
                PsiClient.PREF_OFFICE, PsiClient.DEFAULT_OFFICE);

        refresh.setOnClickListener(v -> load());

        // Show the last reading straight away while the fresh one loads.
        PsiClient.Result cached = PsiClient.load(prefs);
        if (cached != null) latest = cached.readings;
        render(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void load() {
        updated.setText("Updating…");
        refresh.animate().rotationBy(360f).setDuration(700).start();
        executor.execute(() -> {
            try {
                PsiClient.Result result = PsiClient.fetchLatest();
                PsiClient.save(prefs, result);
                main.post(() -> show(result));
            } catch (Exception e) {
                main.post(() -> updated.setText("Couldn't update · " + e.getMessage()));
            }
        });
    }

    private void show(PsiClient.Result result) {
        latest = result.readings;
        String time = PsiClient.shortTime(result.timestamp);
        updated.setText(time.isEmpty() ? "Updated just now" : "NEA update at " + time);
        render(true);
        PsiWidget.updateAll(getApplicationContext());
    }

    private void render(boolean animate) {
        home.render(animate);
        office.render(animate);
        renderRegions();

        // The whole screen takes on the colour of the worse of the two readings.
        Integer worst = home.value();
        Integer other = office.value();
        if (worst == null || (other != null && other > worst)) worst = other;
        int[] colors = PsiClient.bandGradient(worst);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors));
        getWindow().setStatusBarColor(colors[0]);
        getWindow().setNavigationBarColor(colors[1]);
    }

    private void renderRegions() {
        regionsRow.removeAllViews();
        for (int i = 0; i < PsiClient.REGIONS.length; i++) {
            String region = PsiClient.REGIONS[i];
            Integer psi = latest.get(region);

            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER_HORIZONTAL);
            regionsRow.addView(column, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView dot = new TextView(this);
            dot.setGravity(Gravity.CENTER);
            dot.setTextColor(0xFFFFFFFF);
            dot.setTextSize(14);
            dot.setTypeface(Typeface.DEFAULT_BOLD);
            dot.setText(psi == null ? "–" : String.valueOf(psi));
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(psi == null ? 0xFFC5CAD6 : PsiClient.bandColor(psi));
            boolean mine = region.equals(home.region) || region.equals(office.region);
            if (mine) circle.setStroke(dp(3), 0xFF1B1F2A);
            dot.setBackground(circle);
            column.addView(dot, new LinearLayout.LayoutParams(dp(46), dp(46)));

            TextView name = new TextView(this);
            name.setText(PsiClient.REGION_LABELS[i]);
            name.setTextColor(0xFF4A5061);
            name.setTextSize(12);
            name.setPadding(0, dp(6), 0, 0);
            column.addView(name);

            TextView tag = new TextView(this);
            boolean isHome = region.equals(home.region);
            boolean isOffice = region.equals(office.region);
            tag.setText(isHome && isOffice ? "🏠🏢" : isHome ? "🏠" : isOffice ? "🏢" : " ");
            tag.setTextSize(12);
            column.addView(tag);
        }
    }

    private static String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return "Good morning";
        if (hour < 18) return "Good afternoon";
        return "Good evening";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private class Card {
        final String title;
        final String prefKey;
        final TextView regionChip;
        final TextView band;
        final TextView advice;
        final PsiGauge gauge;
        String region;

        Card(View view, String emoji, String title, String prefKey, String defaultRegion) {
            this.title = title;
            this.prefKey = prefKey;
            ((TextView) view.findViewById(R.id.emoji)).setText(emoji);
            ((TextView) view.findViewById(R.id.title)).setText(title);
            regionChip = view.findViewById(R.id.region);
            band = view.findViewById(R.id.band);
            advice = view.findViewById(R.id.advice);
            gauge = view.findViewById(R.id.gauge);
            region = prefs.getString(prefKey, defaultRegion);
            regionChip.setOnClickListener(v -> pickRegion());
        }

        Integer value() {
            return latest.get(region);
        }

        void pickRegion() {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Where is your " + title.toLowerCase(Locale.ROOT) + "?")
                    .setItems(PsiClient.REGION_LABELS, (dialog, which) -> {
                        region = PsiClient.REGIONS[which];
                        prefs.edit().putString(prefKey, region).apply();
                        render(false);
                        gauge.setValue(value(), true);
                        PsiWidget.updateAll(getApplicationContext());
                    })
                    .show();
        }

        void render(boolean animate) {
            regionChip.setText(PsiClient.label(region) + "  ▾");
            Integer psi = value();
            gauge.setValue(psi, animate);

            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(dp(20));
            if (psi == null) {
                band.setText("No reading yet");
                pill.setColor(0xFFC5CAD6);
                advice.setText("Waiting for the latest NEA update.");
            } else {
                band.setText(PsiClient.bandName(psi));
                pill.setColor(PsiClient.bandColor(psi));
                advice.setText(PsiClient.bandAdvice(psi));
            }
            band.setBackground(pill);
        }
    }
}
