package sg.psi.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
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
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    /** Screens at least this wide (unfolded foldables, tablets, landscape) fit more cards per row. */
    private static final int WIDE_DP = 600;
    /** Content never gets wider than this, so cards don't stretch on very large screens. */
    private static final int MAX_CONTENT_DP = 840;
    private static final int MAX_LOCATIONS = 12;
    private static final int REQUEST_LOCATION = 1;
    /** Mini cards are only used for more locations than this. */
    private static final int MINI_ABOVE = 8;

    /**
     * Card sizes, largest first. The screen uses the largest size whose content fits without
     * scrolling; compact sizes stack the gauge above the band and put more cards on each row.
     */
    private enum Level {
        LARGE(128, 20, 16, false, false),
        MEDIUM(104, 16, 14, false, false),
        SMALL(96, 14, 12, true, false),
        TINY(76, 12, 10, true, false),
        /** Small gauge beside the band and name, for many locations on a small screen. */
        MINI(56, 10, 8, true, true);

        final int gaugeDp;
        final int paddingDp;
        final int gapDp;
        final boolean compact;
        final boolean mini;

        Level(int gaugeDp, int paddingDp, int gapDp, boolean compact, boolean mini) {
            this.gaugeDp = gaugeDp;
            this.paddingDp = paddingDp;
            this.gapDp = gapDp;
            this.compact = compact;
            this.mini = mini;
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private ScrollView root;
    private LinearLayout content;
    private View refresh;
    private TextView updated;
    private LinearLayout cards;
    private View regionsCard;
    private LinearLayout regionsRow;
    private final List<Location> locations = new ArrayList<>();
    private final Map<String, Card> cardsById = new LinkedHashMap<>();
    private List<String> order;
    private Level level = Level.LARGE;
    /** Columns and gap the cards were last laid out with, to skip needless re-layouts. */
    private int laidOutColumns;
    private int laidOutGap;
    private String dragging;
    private String lastDropTarget;
    private Map<String, Integer> latest = new LinkedHashMap<>();
    /** Runs once location permission is granted. */
    private Runnable afterPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PsiClient.PREFS, MODE_PRIVATE);
        root = findViewById(R.id.root);
        content = findViewById(R.id.content);
        refresh = findViewById(R.id.refresh);
        updated = findViewById(R.id.updated);
        cards = findViewById(R.id.cards);
        regionsCard = findViewById(R.id.regions_card);
        regionsRow = findViewById(R.id.regions);
        ((TextView) findViewById(R.id.greeting)).setText(greeting());
        ((TextView) findViewById(R.id.date)).setText(
                new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date()));

        refresh.setOnClickListener(v -> load());
        findViewById(R.id.places).setOnClickListener(v -> showLocations());

        locations.addAll(Location.loadAll(prefs));
        for (Location location : locations) cardsById.put(location.id, new Card(location));
        order = Location.loadOrder(prefs, locations);
        regionsCard.setOnLongClickListener(v -> startDrag(Location.REGIONS_CARD, v));
        setUpDropTarget();
        fitContentWidth();
        layoutCards();

        // Show the last reading straight away while the fresh one loads.
        PsiClient.Result cached = PsiClient.load(prefs);
        if (cached != null) latest = cached.readings;
        render(false);

        // Re-fit the cards whenever the screen size changes (rotation, folding, split screen).
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) v.post(this::fitToScreen);
        });

        // First launch: ask where home and office are.
        if (!prefs.getBoolean(PsiClient.PREF_SETUP_DONE, false)) {
            prefs.edit().putBoolean(PsiClient.PREF_SETUP_DONE, true).apply();
            Location home = find("home");
            Location office = find("office");
            if (home != null) pickRegion(home, office == null ? null : () -> pickRegion(office, null));
        }
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
                main.post(() -> updated.setText(latest.isEmpty()
                        ? "Couldn't reach NEA · tap ↻ to try again"
                        : "Offline · showing the last reading"));
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
        for (Card card : cardsById.values()) card.bind(animate);
        renderRegions();

        // The whole screen takes on the colour of the worst reading among the locations.
        Integer worst = null;
        for (Location location : locations) {
            Integer psi = latest.get(location.region);
            if (psi != null && (worst == null || psi > worst)) worst = psi;
        }
        int[] colors = PsiClient.bandGradient(worst);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors));
        getWindow().setStatusBarColor(colors[0]);
        getWindow().setNavigationBarColor(colors[1]);
        root.post(this::fitToScreen);
    }

    private void renderRegions() {
        regionsRow.removeAllViews();
        int size = dp(level.compact ? 38 : 46);
        for (int i = 0; i < PsiClient.REGIONS.length; i++) {
            String region = PsiClient.REGIONS[i];
            Integer psi = latest.get(region);

            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER_HORIZONTAL);
            regionsRow.addView(column, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            StringBuilder icons = new StringBuilder();
            int count = 0;
            for (Location location : locations) {
                if (location.region.equals(region) && count++ < 3) icons.append(location.icon);
            }

            TextView dot = new TextView(this);
            dot.setGravity(Gravity.CENTER);
            dot.setTextColor(0xFFFFFFFF);
            dot.setTextSize(level.compact ? 13 : 14);
            dot.setTypeface(Typeface.DEFAULT_BOLD);
            dot.setText(psi == null ? "–" : String.valueOf(psi));
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(psi == null ? 0xFF3C6B72 : PsiClient.bandColor(psi));
            if (count > 0) circle.setStroke(dp(3), 0xFFFFFFFF);
            dot.setBackground(circle);
            column.addView(dot, new LinearLayout.LayoutParams(size, size));

            TextView name = new TextView(this);
            name.setText(PsiClient.REGION_LABELS[i]);
            name.setTextColor(0xFFB9D6D9);
            name.setTextSize(12);
            name.setGravity(Gravity.CENTER_HORIZONTAL);
            name.setPadding(0, dp(6), 0, 0);
            column.addView(name);

            TextView tag = new TextView(this);
            tag.setText(count == 0 ? " " : icons);
            tag.setTextSize(12);
            tag.setGravity(Gravity.CENTER_HORIZONTAL);
            column.addView(tag);
        }
    }

    // ---- Fitting the cards to the screen ----

    /** Picks the largest card size whose content fits the screen without scrolling. */
    private void fitToScreen() {
        if (dragging != null) return;
        int available = root.getHeight() - root.getPaddingTop() - root.getPaddingBottom();
        int width = root.getWidth() - root.getPaddingLeft() - root.getPaddingRight();
        if (available <= 0 || width <= 0) return;

        // Up to MINI_ABOVE locations keep full-height cards (scrolling if needed); beyond that,
        // mini cards are allowed too.
        boolean allowMini = locations.size() > MINI_ABOVE;
        Level chosen = allowMini ? Level.MINI : Level.TINY;
        for (Level candidate : Level.values()) {
            if (candidate.mini && !allowMini) continue;
            applyLevel(candidate);
            content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            if (content.getMeasuredHeight() <= available) {
                chosen = candidate;
                break;
            }
        }
        if (level != chosen) applyLevel(chosen);
    }

    private void applyLevel(Level next) {
        boolean changed = level != next;
        level = next;
        for (Card card : cardsById.values()) card.applyLevel();
        int pad = dp(level.paddingDp);
        regionsCard.setPadding(pad, pad, pad, pad);
        if (changed) renderRegions();
        // Re-layout only when the grid changes; moving cards restarts their gauges.
        if (columns() != laidOutColumns || dp(level.gapDp) != laidOutGap) layoutCards();
    }

    private int columns() {
        boolean wide = getResources().getConfiguration().screenWidthDp >= WIDE_DP;
        if (wide) return level.compact ? 3 : 2;
        return level.compact ? 2 : 1;
    }

    private void fitContentWidth() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        if (widthDp <= MAX_CONTENT_DP) return;
        int side = dp((widthDp - MAX_CONTENT_DP) / 2 + 20);
        content.setPadding(side, content.getPaddingTop(), side, content.getPaddingBottom());
    }

    /**
     * Places the cards in the saved order, several per row when there is room.
     * Across Singapore always gets a full row of its own.
     */
    private void layoutCards() {
        for (String key : order) detach(viewFor(key));
        cards.removeAllViews();

        int columns = columns();
        laidOutColumns = columns;
        laidOutGap = dp(level.gapDp);
        List<View> row = new ArrayList<>();
        for (String key : order) {
            if (key.equals(Location.REGIONS_CARD)) {
                addRow(row, columns);
                row.clear();
                addRow(Collections.singletonList(regionsCard), 1);
                continue;
            }
            row.add(viewFor(key));
            if (row.size() == columns) {
                addRow(row, columns);
                row.clear();
            }
        }
        addRow(row, columns);
    }

    private void addRow(List<View> views, int columns) {
        if (views.isEmpty()) return;
        int gap = dp(level.gapDp);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = gap;
        if (columns == 1) {
            cards.addView(views.get(0), rowParams);
            return;
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        for (int i = 0; i < columns; i++) {
            // Empty slots keep a short last row's cards the same width as the others.
            View view = i < views.size() ? views.get(i) : new Space(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) params.setMarginStart(gap);
            row.addView(view, params);
        }
        cards.addView(row, rowParams);
    }

    private View viewFor(String key) {
        return key.equals(Location.REGIONS_CARD) ? regionsCard : cardsById.get(key).view;
    }

    private static void detach(View view) {
        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent != null) parent.removeView(view);
    }

    // ---- Rearranging ----

    /** Long-press a card to pick it up; the other cards make room as it is dragged over them. */
    private boolean startDrag(String key, View view) {
        dragging = key;
        lastDropTarget = null;
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        view.startDragAndDrop(ClipData.newPlainText("card", key), new View.DragShadowBuilder(view), null, 0);
        view.setAlpha(0.35f);
        return true;
    }

    private void setUpDropTarget() {
        cards.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return dragging != null;
                case DragEvent.ACTION_DRAG_LOCATION:
                    moveDraggedCard(event.getX(), event.getY());
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    if (dragging != null) {
                        viewFor(dragging).setAlpha(1f);
                        dragging = null;
                        save();
                        root.post(this::fitToScreen);
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
            View card = viewFor(key);
            card.getLocationOnScreen(loc);
            boolean inside = px >= loc[0] && px < loc[0] + card.getWidth()
                    && py >= loc[1] && py < loc[1] + card.getHeight();
            if (!inside) continue;
            // Cards differ in size, so after a swap the pointer can still be over the same
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

    // ---- Finding a place's region ----

    /** Receives the phone's position, or a message explaining why it couldn't be found. */
    private interface PositionCallback {
        void onPosition(double lat, double lon);

        void onError(String message);
    }

    /** Gets the phone's approximate position once, asking for permission the first time. */
    private void currentPosition(PositionCallback callback) {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            afterPermission = () -> currentPosition(callback);
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
            return;
        }
        LocationManager manager = getSystemService(LocationManager.class);
        // Approximate location only allows the network (or fused) provider, not GPS.
        String provider = null;
        if (Build.VERSION.SDK_INT >= 31 && manager.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
            provider = LocationManager.FUSED_PROVIDER;
        } else if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            provider = LocationManager.NETWORK_PROVIDER;
        }
        if (provider == null) {
            callback.onError("Turn on Location in your phone's settings, then try again.");
            return;
        }
        try {
            android.location.Location last = manager.getLastKnownLocation(provider);
            if (last != null && System.currentTimeMillis() - last.getTime() < 10 * 60 * 1000) {
                callback.onPosition(last.getLatitude(), last.getLongitude());
                return;
            }
            if (Build.VERSION.SDK_INT >= 30) {
                manager.getCurrentLocation(provider, null, getMainExecutor(),
                        location -> deliverPosition(location, callback));
            } else {
                manager.requestSingleUpdate(provider, new LocationListener() {
                    @Override
                    public void onLocationChanged(android.location.Location location) {
                        deliverPosition(location, callback);
                    }

                    // Older Android versions require these to be implemented.
                    @Override
                    public void onStatusChanged(String p, int status, Bundle extras) {
                    }

                    @Override
                    public void onProviderEnabled(String p) {
                    }

                    @Override
                    public void onProviderDisabled(String p) {
                    }
                }, Looper.getMainLooper());
            }
        } catch (SecurityException e) {
            callback.onError("Location permission is needed for this.");
        }
    }

    private static void deliverPosition(android.location.Location location, PositionCallback callback) {
        if (location == null) {
            callback.onError("Couldn't get your location. Check that Location is on and try again.");
        } else if (!RegionFinder.inSingapore(location.getLatitude(), location.getLongitude())) {
            callback.onError("You don't seem to be in Singapore right now.");
        } else {
            callback.onPosition(location.getLatitude(), location.getLongitude());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode != REQUEST_LOCATION) return;
        Runnable next = afterPermission;
        afterPermission = null;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            if (next != null) next.run();
        } else {
            toast("Without location access, pick the region yourself or search by postal code.");
        }
    }

    /** The card's GPS button: sets this location's region from where the phone is now. */
    private void locateHere(Location location) {
        toast("Finding your location…");
        currentPosition(new PositionCallback() {
            @Override
            public void onPosition(double lat, double lon) {
                location.region = RegionFinder.nearestRegion(lat, lon);
                location.address = "";
                locationsChanged(location);
                toast(location.name + " set to " + PsiClient.label(location.region) + " from your location");
            }

            @Override
            public void onError(String message) {
                toast(message);
            }
        });
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    // ---- Managing locations ----

    private void showLocations() {
        List<String> items = new ArrayList<>();
        for (Location location : locations) {
            items.add(location.icon + "  " + location.name + " · " + PsiClient.label(location.region));
        }
        boolean canAdd = locations.size() < MAX_LOCATIONS;
        items.add(canAdd ? "＋  Add a location" : "You can have up to " + MAX_LOCATIONS + " locations");
        new AlertDialog.Builder(this)
                .setTitle("Your locations")
                .setItems(items.toArray(new String[0]), (dialog, which) -> {
                    if (which < locations.size()) editLocation(locations.get(which));
                    else if (canAdd) editLocation(null);
                })
                .show();
    }

    /** Add a location ({@code existing} null) or edit one: name, icon and region. */
    private void editLocation(Location existing) {
        boolean adding = existing == null;
        String[] icon = {adding ? "📍" : existing.icon};
        String[] address = {adding ? "" : existing.address};

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), 0);

        form.addView(formLabel("Name"));
        EditText name = new EditText(this);
        name.setSingleLine();
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});
        name.setHint("e.g. Gym, Mum's place");
        if (!adding) name.setText(existing.name);
        form.addView(name);

        form.addView(formLabel("Icon"));
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(6);
        List<TextView> iconViews = new ArrayList<>();
        for (String emoji : Location.ICONS) {
            TextView option = new TextView(this);
            option.setText(emoji);
            option.setTextSize(24);
            option.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(46);
            params.height = dp(46);
            option.setLayoutParams(params);
            option.setOnClickListener(v -> {
                icon[0] = emoji;
                highlightIcon(iconViews, emoji);
            });
            grid.addView(option);
            iconViews.add(option);
        }
        highlightIcon(iconViews, icon[0]);
        form.addView(grid);

        // Region: found from a postal code, an address or the phone's location, or picked below.
        form.addView(formLabel("Region"));
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText search = new EditText(this);
        search.setSingleLine();
        search.setHint("Postal code or address");
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        if (!adding) search.setText(existing.address);
        searchRow.addView(search, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button find = new Button(this);
        find.setText("Find");
        searchRow.addView(find);
        form.addView(searchRow);

        Button here = new Button(this, null, android.R.attr.borderlessButtonStyle);
        here.setText("📍  Use my current location");
        here.setAllCaps(false);
        here.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        form.addView(here);

        TextView found = new TextView(this);
        found.setTextSize(13);
        found.setTextColor(0xFF6B7280);
        found.setPadding(0, 0, 0, dp(6));
        found.setVisibility(View.GONE);
        form.addView(found);

        RadioGroup regions = new RadioGroup(this);
        String current = adding ? PsiClient.DEFAULT_OFFICE : existing.region;
        Map<String, Integer> regionIds = new LinkedHashMap<>();
        for (int i = 0; i < PsiClient.REGIONS.length; i++) {
            RadioButton option = new RadioButton(this);
            option.setId(View.generateViewId());
            option.setText(regionItem(i));
            option.setTag(PsiClient.REGIONS[i]);
            option.setPadding(dp(8), dp(6), 0, dp(6));
            regions.addView(option);
            regionIds.put(PsiClient.REGIONS[i], option.getId());
            if (PsiClient.REGIONS[i].equals(current)) regions.check(option.getId());
        }
        form.addView(regions);

        Runnable runSearch = () -> {
            String query = search.getText().toString().trim();
            if (query.isEmpty()) return;
            found.setVisibility(View.VISIBLE);
            found.setText("Searching…");
            executor.execute(() -> {
                RegionFinder.Match match = RegionFinder.search(getApplicationContext(), query);
                main.post(() -> {
                    if (match == null) {
                        found.setText("Couldn't find that. Check the postal code or address, or pick a region below.");
                        return;
                    }
                    regions.check(regionIds.get(match.region));
                    address[0] = match.address;
                    found.setText("✓ " + match.address + " → " + PsiClient.label(match.region));
                });
            });
        };
        find.setOnClickListener(v -> runSearch.run());
        search.setOnEditorActionListener((v, actionId, event) -> {
            runSearch.run();
            return true;
        });
        here.setOnClickListener(v -> {
            found.setVisibility(View.VISIBLE);
            found.setText("Finding your location…");
            currentPosition(new PositionCallback() {
                @Override
                public void onPosition(double lat, double lon) {
                    String region = RegionFinder.nearestRegion(lat, lon);
                    regions.check(regionIds.get(region));
                    address[0] = "";
                    found.setText("✓ Your current location → " + PsiClient.label(region));
                }

                @Override
                public void onError(String message) {
                    found.setText(message);
                }
            });
        });

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(adding ? "Add a location" : "Edit " + existing.name)
                .setView(scroll)
                .setPositiveButton(adding ? "Add" : "Save", (dialog, which) -> {
                    RadioButton checked = regions.findViewById(regions.getCheckedRadioButtonId());
                    String region = checked != null ? (String) checked.getTag() : current;
                    String label = name.getText().toString().trim();
                    if (adding) {
                        if (label.isEmpty()) label = "Place " + (locations.size() + 1);
                        Location added = new Location("loc" + System.currentTimeMillis(), label, icon[0], region);
                        added.address = address[0];
                        addLocation(added);
                    } else {
                        if (!label.isEmpty()) existing.name = label;
                        existing.icon = icon[0];
                        existing.region = region;
                        existing.address = address[0];
                        locationsChanged(existing);
                    }
                })
                .setNegativeButton("Cancel", null);
        if (!adding && locations.size() > 1) {
            builder.setNeutralButton("Delete", (dialog, which) -> confirmDelete(existing));
        }
        builder.show();
    }

    private TextView formLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(13);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextColor(0xFF6B7280);
        label.setPadding(0, dp(14), 0, dp(4));
        return label;
    }

    private void highlightIcon(List<TextView> options, String selected) {
        for (TextView option : options) {
            if (option.getText().toString().equals(selected)) {
                GradientDrawable ring = new GradientDrawable();
                ring.setShape(GradientDrawable.OVAL);
                ring.setColor(0xFFD6ECEE);
                ring.setStroke(dp(2), 0xFF0E3B43);
                option.setBackground(ring);
            } else {
                option.setBackground(null);
            }
        }
    }

    private void confirmDelete(Location location) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + location.name + "?")
                .setMessage("Its card will be removed. You can add it again later.")
                .setPositiveButton("Delete", (dialog, which) -> removeLocation(location))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addLocation(Location location) {
        locations.add(location);
        Card card = new Card(location);
        cardsById.put(location.id, card);
        // New cards go after the last location card.
        int at = 0;
        for (int i = 0; i < order.size(); i++) {
            if (!order.get(i).equals(Location.REGIONS_CARD)) at = i + 1;
        }
        order.add(at, location.id);
        save();
        layoutCards();
        render(false);
        card.gauge.setValue(card.value(), true);
    }

    private void removeLocation(Location location) {
        locations.remove(location);
        Card card = cardsById.remove(location.id);
        if (card != null) detach(card.view);
        order.remove(location.id);
        save();
        layoutCards();
        render(false);
    }

    private void locationsChanged(Location changed) {
        save();
        render(false);
        Card card = cardsById.get(changed.id);
        if (card != null) card.gauge.setValue(card.value(), true);
    }

    private void save() {
        Location.saveAll(prefs, locations);
        Location.saveOrder(prefs, order);
        PsiWidget.updateAll(getApplicationContext());
    }

    private Location find(String id) {
        for (Location location : locations) {
            if (location.id.equals(id)) return location;
        }
        return null;
    }

    /** Shows the region list with example towns; runs {@code then} after a region is picked. */
    private void pickRegion(Location location, Runnable then) {
        CharSequence[] items = new CharSequence[PsiClient.REGIONS.length];
        int checked = 0;
        for (int i = 0; i < items.length; i++) {
            items[i] = regionItem(i);
            if (PsiClient.REGIONS[i].equals(location.region)) checked = i;
        }
        String title = location.id.equals("home") || location.id.equals("office")
                ? "Where is your " + location.name.toLowerCase(Locale.ROOT) + "?"
                : "Which region is " + location.name + " in?";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    dialog.dismiss();
                    location.region = PsiClient.REGIONS[which];
                    locationsChanged(location);
                    if (then != null) then.run();
                })
                .show();
    }

    /** "East" with its example towns in smaller grey text underneath. */
    private static CharSequence regionItem(int i) {
        SpannableStringBuilder item = new SpannableStringBuilder(PsiClient.REGION_LABELS[i]);
        int start = item.length();
        item.append("\n").append(PsiClient.REGION_AREAS[i]);
        item.setSpan(new RelativeSizeSpan(0.8f), start, item.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        item.setSpan(new ForegroundColorSpan(0xFF6B7280), start, item.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return item;
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

    /** One location's card: icon, name, region chip, ring gauge, band and health tip. */
    private class Card {
        final Location location;
        final View view;
        final TextView emoji;
        final TextView title;
        final TextView regionChip;
        final TextView band;
        final TextView advice;
        final PsiGauge gauge;
        final LinearLayout body;
        final LinearLayout info;
        final View header;
        final View gps;

        Card(Location location) {
            this.location = location;
            view = getLayoutInflater().inflate(R.layout.psi_card, cards, false);
            emoji = view.findViewById(R.id.emoji);
            title = view.findViewById(R.id.title);
            regionChip = view.findViewById(R.id.region);
            band = view.findViewById(R.id.band);
            advice = view.findViewById(R.id.advice);
            gauge = view.findViewById(R.id.gauge);
            body = view.findViewById(R.id.body);
            info = view.findViewById(R.id.info);
            header = view.findViewById(R.id.header);
            gps = view.findViewById(R.id.gps);
            gps.setOnClickListener(v -> locateHere(location));
            title.setEllipsize(TextUtils.TruncateAt.END);
            regionChip.setOnClickListener(v -> pickRegion(location, null));
            view.setOnClickListener(v -> editLocation(location));
            view.setOnLongClickListener(v -> startDrag(location.id, v));
            applyLevel();
        }

        Integer value() {
            return latest.get(location.region);
        }

        void bind(boolean animate) {
            gauge.setValue(value(), animate);
            updateTexts();
        }

        /** Large sizes put the band and tip beside the gauge; compact ones stack them under it. */
        void applyLevel() {
            boolean compact = level.compact && !level.mini;
            int pad = dp(level.paddingDp);
            view.setPadding(pad, pad, pad, pad);

            LinearLayout.LayoutParams gaugeParams = (LinearLayout.LayoutParams) gauge.getLayoutParams();
            gaugeParams.width = dp(level.gaugeDp);
            gaugeParams.height = dp(level.gaugeDp);
            gauge.setLayoutParams(gaugeParams);

            body.setOrientation(compact ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
            body.setGravity(compact ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams infoParams = (LinearLayout.LayoutParams) info.getLayoutParams();
            infoParams.width = compact ? LinearLayout.LayoutParams.WRAP_CONTENT : 0;
            infoParams.weight = compact ? 0 : 1;
            infoParams.setMarginStart(compact ? 0 : dp(18));
            infoParams.topMargin = compact ? dp(6) : 0;
            info.setLayoutParams(infoParams);
            info.setGravity(compact ? Gravity.CENTER_HORIZONTAL : Gravity.START);
            advice.setGravity(compact ? Gravity.CENTER_HORIZONTAL : Gravity.START);
            advice.setTextSize(compact ? 13 : 15);

            if (level.mini) {
                // Gauge on the left; band and name on the right; no header row.
                body.setOrientation(LinearLayout.HORIZONTAL);
                body.setGravity(Gravity.CENTER_VERTICAL);
                infoParams.width = 0;
                infoParams.weight = 1;
                infoParams.setMarginStart(dp(10));
                infoParams.topMargin = 0;
                info.setLayoutParams(infoParams);
                LinearLayout.LayoutParams bodyParams = (LinearLayout.LayoutParams) body.getLayoutParams();
                bodyParams.topMargin = 0;
                body.setLayoutParams(bodyParams);
                advice.setTextSize(13);
                advice.setMaxLines(1);
                advice.setEllipsize(TextUtils.TruncateAt.END);
            } else {
                LinearLayout.LayoutParams bodyParams = (LinearLayout.LayoutParams) body.getLayoutParams();
                bodyParams.topMargin = dp(12);
                body.setLayoutParams(bodyParams);
                advice.setMaxLines(Integer.MAX_VALUE);
                advice.setEllipsize(null);
            }
            header.setVisibility(level.mini ? View.GONE : View.VISIBLE);
            band.setTextSize(level.mini ? 12 : 14);
            regionChip.setVisibility(level.compact ? View.GONE : View.VISIBLE);
            // Names may wrap to a second line rather than being cut off.
            title.setMaxLines(2);
            emoji.setTextSize(compact ? 18 : 22);
            title.setTextSize(compact ? 16 : 19);
            updateTexts();
        }

        void updateTexts() {
            emoji.setText(location.icon);
            title.setText(location.name);
            regionChip.setText(PsiClient.label(location.region) + "  ▾");
            Integer psi = value();

            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(dp(20));
            pill.setColor(psi == null ? 0xFF3C6B72 : PsiClient.bandColor(psi));
            band.setBackground(pill);
            band.setText(psi == null ? (level.compact ? "No reading" : "No reading yet") : PsiClient.bandName(psi));
            if (level.mini) {
                // The header row is hidden, so the name goes under the band.
                advice.setText(location.icon + " " + location.name);
            } else if (level.compact) {
                // No room for the tip; show the region instead, since the chip is hidden.
                advice.setText(PsiClient.label(location.region));
            } else {
                advice.setText(psi == null ? "Waiting for the latest NEA update." : PsiClient.bandAdvice(psi));
            }
        }
    }
}
