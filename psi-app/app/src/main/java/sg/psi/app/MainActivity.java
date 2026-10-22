package sg.psi.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

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

        prefs = getSharedPreferences(PsiClient.PREFS, MODE_PRIVATE);
        updated = findViewById(R.id.updated);
        allRegions = findViewById(R.id.all_regions);
        home = new Card(findViewById(R.id.home_card), "Home", PsiClient.PREF_HOME, PsiClient.DEFAULT_HOME);
        office = new Card(findViewById(R.id.office_card), "Office", PsiClient.PREF_OFFICE, PsiClient.DEFAULT_OFFICE);

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
            try {
                PsiClient.Result result = PsiClient.fetchLatest();
                PsiClient.save(prefs, result);
                main.post(() -> show(result));
            } catch (Exception e) {
                main.post(() -> updated.setText("Could not load PSI: " + e.getMessage()));
            }
        });
    }

    private void show(PsiClient.Result result) {
        latest = result.readings;
        updated.setText("Updated " + result.timestamp);
        home.render();
        office.render();

        StringBuilder sb = new StringBuilder("All regions: ");
        for (int i = 0; i < PsiClient.REGIONS.length; i++) {
            Integer v = latest.get(PsiClient.REGIONS[i]);
            if (i > 0) sb.append("  ·  ");
            sb.append(PsiClient.REGION_LABELS[i]).append(' ').append(v == null ? "–" : v);
        }
        allRegions.setText(sb);
        PsiWidget.updateAll(getApplicationContext());
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
                    android.R.layout.simple_spinner_item, PsiClient.REGION_LABELS);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            region.setAdapter(adapter);

            String saved = prefs.getString(prefKey, defaultRegion);
            for (int i = 0; i < PsiClient.REGIONS.length; i++) {
                if (PsiClient.REGIONS[i].equals(saved)) region.setSelection(i);
            }
            region.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    prefs.edit().putString(Card.this.prefKey, PsiClient.REGIONS[pos]).apply();
                    render();
                    PsiWidget.updateAll(getApplicationContext());
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }

        void render() {
            Integer psi = latest.get(PsiClient.REGIONS[region.getSelectedItemPosition()]);
            if (psi == null) {
                value.setText("–");
                band.setVisibility(View.INVISIBLE);
                return;
            }
            value.setText(String.valueOf(psi));
            band.setText(PsiClient.bandName(psi));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(40f);
            bg.setColor(PsiClient.bandColor(psi));
            band.setBackground(bg);
            band.setVisibility(View.VISIBLE);
        }
    }
}
