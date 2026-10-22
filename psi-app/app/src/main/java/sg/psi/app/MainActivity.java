package sg.psi.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    /** Screens at least this wide (unfolded foldables, tablets, landscape) show two cards per row. */
    private static final int WIDE_DP = 600;
    /** Content never gets wider than this, so cards don't stretch on very large screens. */
    private static final int MAX_CONTENT_DP = 840;
    private static final List<String> DEFAULT_ORDER = Arrays.asList("home", "office", "regions");

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private View root;
    private View refresh;
    private TextView updated;
    private LinearLayout regionsRow;
    private LinearLayout cards;
    private Card home;
    private Card office;
    private Map<String, Integer> latest = new LinkedHashMap<>();
    private final Map<String, View> cardViews = new LinkedHashMap<>();
    private List<String> order;
    private String dragging;
    private String lastDropTarget;

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
        findViewById(R.id.places).setOnClickListener(v -> choosePlaces());

        cards = findViewById(R.id.cards);
        cardViews.put("home", findViewById(R.id.home_card));
        cardViews.put("office", findViewById(R.id.office_card));
        cardViews.put("regions", findViewById(R.id.regions_card));
        order = loadOrder();
        fitContentWidth();
        layoutCards();
        setUpDragging();

        // Show the last reading straight away while the fresh one loads.
        PsiClient.Result cached = PsiClient.load(prefs);
        if (cached != null) latest = cached.readings;
        render(false);

        // First launch: ask where home and office are.
        if (!prefs.getBoolean(PsiClient.PREF_SETUP_DONE, false)) {
            prefs.edit().putBoolean(PsiClient.PREF_SETUP_DONE, true).apply();
            home.pickRegion(() -> office.pickRegion(null));
        }
    }

    private void choosePlaces() {
        String[] items = {
                "🏠  Home · " + PsiClient.label(home.region),
                "🏢  Office · " + PsiClient.label(office.region),
        };
        new AlertDialog.Builder(this)
                .setTitle("Your places")
                .setItems(items, (dialog, which) -> (which == 0 ? home : office).pickRegion(null))
                .show();
    }

    private List<String> loadOrder() {
        List<String> result = new ArrayList<>();
        String saved = prefs.getString(PsiClient.PREF_CARD_ORDER, "");
        for (String key : saved.split(",")) {
            if (DEFAULT_ORDER.contains(key) && !result.contains(key)) result.add(key);
        }
        for (String key : DEFAULT_ORDER) {
            if (!result.contains(key)) result.add(key);
        }
        return result;
    }

    private void fitContentWidth() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        if (widthDp <= MAX_CONTENT_DP) return;
        View content = findViewById(R.id.content);
        int side = dp((widthDp - MAX_CONTENT_DP) / 2 + 20);
        content.setPadding(side, content.getPaddingTop(), side, content.getPaddingBottom());
    }

    /** Places the cards in the saved order: one column, or two per row on wide screens. */
    private void layoutCards() {
        for (View card : cardViews.values()) {
            ViewGroup parent = (ViewGroup) card.getParent();
            if (parent != null) parent.removeView(card);
        }
        cards.removeAllViews();

        boolean wide = getResources().getConfiguration().screenWidthDp >= WIDE_DP;
        int gap = dp(16);
        for (int i = 0; i < order.size(); i += wide ? 2 : 1) {
            View first = cardViews.get(order.get(i));
            View second = wide && i + 1 < order.size() ? cardViews.get(order.get(i + 1)) : null;
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = gap;
            if (second == null) {
                cards.addView(first, rowParams);
                continue;
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBaselineAligned(false);
            LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            left.setMarginEnd(gap / 2);
            LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            right.setMarginStart(gap / 2);
            row.addView(first, left);
            row.addView(second, right);
            cards.addView(row, rowParams);
        }
    }

    /** Long-press a card to pick it up; the other cards make room as it is dragged over them. */
    private void setUpDragging() {
        for (Map.Entry<String, View> entry : cardViews.entrySet()) {
            String key = entry.getKey();
            entry.getValue().setOnLongClickListener(v -> {
                dragging = key;
                lastDropTarget = null;
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                v.startDragAndDrop(ClipData.newPlainText("card", key),
                        new View.DragShadowBuilder(v), null, 0);
                v.setAlpha(0.35f);
                return true;
            });
        }
        cards.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return dragging != null;
                case DragEvent.ACTION_DRAG_LOCATION:
                    moveDraggedCard(event.getX(), event.getY());
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    if (dragging != null) {
                        cardViews.get(dragging).setAlpha(1f);
                        prefs.edit().putString(PsiClient.PREF_CARD_ORDER, TextUtils.join(",", order)).apply();
                        dragging = null;
                    }
                    return true;
                default:
                    return true;
            }
        });
    }

    private void moveDraggedCard(float x, float y) {
        int[] origin = new int[2];
        cards.getLocationOnScreen(origin);
        int px = origin[0] + Math.round(x);
        int py = origin[1] + Math.round(y);
        int[] loc = new int[2];
        for (String key : order) {
            if (key.equals(dragging)) continue;
            View card = cardViews.get(key);
            card.getLocationOnScreen(loc);
            boolean inside = px >= loc[0] && px < loc[0] + card.getWidth()
                    && py >= loc[1] && py < loc[1] + card.getHeight();
            if (!inside) continue;
            // Cards differ in height, so after a swap the pointer can still be over the same
            // card; wait until it leaves before swapping again to avoid flicking back and forth.
            if (key.equals(lastDropTarget)) return;
            int from = order.indexOf(dragging);
            int to = order.indexOf(key);
            order.remove(from);
            order.add(to, dragging);
            lastDropTarget = key;
            layoutCards();
            return;
        }
        lastDropTarget = null;
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
            circle.setColor(psi == null ? 0xFF3C6B72 : PsiClient.bandColor(psi));
            boolean mine = region.equals(home.region) || region.equals(office.region);
            if (mine) circle.setStroke(dp(3), 0xFFFFFFFF);
            dot.setBackground(circle);
            column.addView(dot, new LinearLayout.LayoutParams(dp(46), dp(46)));

            TextView name = new TextView(this);
            name.setText(PsiClient.REGION_LABELS[i]);
            name.setTextColor(0xFFB9D6D9);
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
            regionChip.setOnClickListener(v -> pickRegion(null));
        }

        Integer value() {
            return latest.get(region);
        }

        /** Shows the region list with example towns; runs {@code then} after a region is picked. */
        void pickRegion(Runnable then) {
            CharSequence[] items = new CharSequence[PsiClient.REGIONS.length];
            int checked = 0;
            for (int i = 0; i < items.length; i++) {
                SpannableStringBuilder item = new SpannableStringBuilder(PsiClient.REGION_LABELS[i]);
                int start = item.length();
                item.append("\n").append(PsiClient.REGION_AREAS[i]);
                item.setSpan(new RelativeSizeSpan(0.8f), start, item.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                item.setSpan(new ForegroundColorSpan(0xFF6B7280), start, item.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                items[i] = item;
                if (PsiClient.REGIONS[i].equals(region)) checked = i;
            }
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Where is your " + title.toLowerCase(Locale.ROOT) + "?")
                    .setSingleChoiceItems(items, checked, (dialog, which) -> {
                        dialog.dismiss();
                        region = PsiClient.REGIONS[which];
                        prefs.edit().putString(prefKey, region).apply();
                        MainActivity.this.render(false);
                        gauge.setValue(value(), true);
                        PsiWidget.updateAll(getApplicationContext());
                        if (then != null) then.run();
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
                pill.setColor(0xFF3C6B72);
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
