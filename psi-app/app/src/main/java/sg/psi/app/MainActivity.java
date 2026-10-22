package sg.psi.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    // NEA PSI readings published on data.gov.sg (v2 first, v1 as a fallback).
    private static final String[] PSI_URLS = {
            "https://api-open.data.gov.sg/v2/real-time/api/psi",
            "https://api.data.gov.sg/v1/environment/psi",
    };

    private static final String[] REGIONS = {"north", "south", "east", "west", "central"};
    private static final String[] REGION_LABELS = {"North", "South", "East", "West", "Central"};

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private Card home;
    private Card office;
    private TextView updated;
    private TextView allRegions;
    private Map<String, Integer> latest = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("psi", MODE_PRIVATE);
        updated = findViewById(R.id.updated);
        allRegions = findViewById(R.id.all_regions);
        home = new Card(findViewById(R.id.home_card), "Home", "home_region", "east");
        office = new Card(findViewById(R.id.office_card), "Office", "office_region", "central");

        findViewById(R.id.refresh).setOnClickListener(v -> load());
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
        updated.setText("Loading…");
        executor.execute(() -> {
            Exception error = null;
            for (String url : PSI_URLS) {
                try {
                    Result result = parse(fetch(url));
                    main.post(() -> show(result));
                    return;
                } catch (Exception e) {
                    error = e;
                }
            }
            String message = error == null ? "unknown error" : error.getMessage();
            main.post(() -> updated.setText("Could not load PSI: " + message));
        });
    }

    private void show(Result result) {
        latest = result.readings;
        updated.setText("Updated " + result.timestamp);
        home.render();
        office.render();

        StringBuilder sb = new StringBuilder("All regions: ");
        for (int i = 0; i < REGIONS.length; i++) {
            Integer v = latest.get(REGIONS[i]);
            if (i > 0) sb.append("  ·  ");
            sb.append(REGION_LABELS[i]).append(' ').append(v == null ? "–" : v);
        }
        allRegions.setText(sb);
    }

    private static String fetch(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        try {
            int code = conn.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    // Handles both the v2 shape ({data: {items: [...]}}) and the v1 shape ({items: [...]}).
    private static Result parse(String body) throws Exception {
        JSONObject root = new JSONObject(body);
        JSONObject data = root.optJSONObject("data");
        if (data == null) data = root;

        JSONArray items = data.getJSONArray("items");
        if (items.length() == 0) throw new Exception("no readings");
        JSONObject item = items.getJSONObject(items.length() - 1);
        JSONObject psi = item.getJSONObject("readings").getJSONObject("psi_twenty_four_hourly");

        Result result = new Result();
        for (String region : REGIONS) {
            if (psi.has(region)) result.readings.put(region, psi.getInt(region));
        }
        if (result.readings.isEmpty()) throw new Exception("no regional readings");

        String ts = item.optString("updatedTimestamp",
                item.optString("update_timestamp", item.optString("timestamp", "")));
        result.timestamp = ts.replace('T', ' ').replaceAll("\\+08:00$", "");
        return result;
    }

    private static int bandColor(int psi) {
        if (psi <= 50) return Color.parseColor("#2E9E4F");
        if (psi <= 100) return Color.parseColor("#2F7BD8");
        if (psi <= 200) return Color.parseColor("#E8A317");
        if (psi <= 300) return Color.parseColor("#D9481E");
        return Color.parseColor("#8B1A1A");
    }

    private static String bandName(int psi) {
        if (psi <= 50) return "Good";
        if (psi <= 100) return "Moderate";
        if (psi <= 200) return "Unhealthy";
        if (psi <= 300) return "Very unhealthy";
        return "Hazardous";
    }

    private static class Result {
        final Map<String, Integer> readings = new LinkedHashMap<>();
        String timestamp = "";
    }

    private class Card {
        final TextView value;
        final TextView band;
        final Spinner region;
        final String prefKey;

        Card(View root, String label, String prefKey, String defaultRegion) {
            this.prefKey = prefKey;
            ((TextView) root.findViewById(R.id.label)).setText(label);
            value = root.findViewById(R.id.value);
            band = root.findViewById(R.id.band);
            region = root.findViewById(R.id.region);

            ArrayAdapter<String> adapter = new ArrayAdapter<>(MainActivity.this,
                    android.R.layout.simple_spinner_item, REGION_LABELS);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            region.setAdapter(adapter);

            String saved = prefs.getString(prefKey, defaultRegion);
            for (int i = 0; i < REGIONS.length; i++) {
                if (REGIONS[i].equals(saved)) region.setSelection(i);
            }
            region.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    prefs.edit().putString(Card.this.prefKey, REGIONS[pos]).apply();
                    render();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }

        void render() {
            Integer psi = latest.get(REGIONS[region.getSelectedItemPosition()]);
            if (psi == null) {
                value.setText("–");
                band.setVisibility(View.INVISIBLE);
                return;
            }
            value.setText(String.valueOf(psi));
            band.setText(bandName(psi));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(40f);
            bg.setColor(bandColor(psi));
            band.setBackground(bg);
            band.setVisibility(View.VISIBLE);
        }
    }
}
