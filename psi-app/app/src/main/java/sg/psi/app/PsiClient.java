package sg.psi.app;

import android.content.SharedPreferences;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Fetches NEA 24-hr PSI readings and holds the shared region/band helpers. */
final class PsiClient {

    // NEA PSI readings published on data.gov.sg (v2 first, v1 as a fallback).
    private static final String[] PSI_URLS = {
            "https://api-open.data.gov.sg/v2/real-time/api/psi",
            "https://api.data.gov.sg/v1/environment/psi",
    };

    static final String[] REGIONS = {"north", "south", "east", "west", "central"};
    static final String[] REGION_LABELS = {"North", "South", "East", "West", "Central"};
    /** Example towns in each NEA region, to help people pick theirs. */
    static final String[] REGION_AREAS = {
            "Woodlands, Yishun, Sembawang",
            "Bukit Merah, Telok Blangah, Sentosa",
            "Tampines, Bedok, Pasir Ris, Changi",
            "Jurong, Clementi, Bukit Batok, Choa Chu Kang",
            "Toa Payoh, Bishan, Orchard, Novena",
    };

    static final String PREFS = "psi";
    static final String PREF_HOME = "home_region";
    static final String PREF_OFFICE = "office_region";
    static final String DEFAULT_HOME = "east";
    static final String DEFAULT_OFFICE = "central";
    static final String PREF_SETUP_DONE = "setup_done";
    static final String PREF_CARD_ORDER = "card_order";
    private static final String PREF_CACHE = "last_result";

    private PsiClient() {
    }

    static class Result {
        final Map<String, Integer> readings = new LinkedHashMap<>();
        String timestamp = "";
    }

    static Result fetchLatest() throws Exception {
        Exception error = null;
        for (String url : PSI_URLS) {
            try {
                return parse(fetch(url));
            } catch (Exception e) {
                error = e;
            }
        }
        throw error;
    }

    /** Keeps the last good reading so the widget has something to show when offline. */
    static void save(SharedPreferences prefs, Result result) {
        try {
            JSONObject o = new JSONObject();
            o.put("timestamp", result.timestamp);
            o.put("readings", new JSONObject(result.readings));
            prefs.edit().putString(PREF_CACHE, o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static Result load(SharedPreferences prefs) {
        String s = prefs.getString(PREF_CACHE, null);
        if (s == null) return null;
        try {
            JSONObject o = new JSONObject(s);
            Result result = new Result();
            result.timestamp = o.optString("timestamp");
            JSONObject readings = o.getJSONObject("readings");
            for (String region : REGIONS) {
                if (readings.has(region)) result.readings.put(region, readings.getInt(region));
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    static String label(String region) {
        for (int i = 0; i < REGIONS.length; i++) {
            if (REGIONS[i].equals(region)) return REGION_LABELS[i];
        }
        return region;
    }

    static int bandColor(int psi) {
        if (psi <= 50) return Color.parseColor("#1FA463");
        if (psi <= 100) return Color.parseColor("#2F80ED");
        if (psi <= 200) return Color.parseColor("#F2994A");
        if (psi <= 300) return Color.parseColor("#EB5757");
        return Color.parseColor("#8E2042");
    }

    /** Top and bottom colours of the screen background; null means no reading yet. */
    static int[] bandGradient(Integer psi) {
        if (psi == null) return new int[]{0xFF3A4A7A, 0xFF182848};
        if (psi <= 50) return new int[]{0xFF0F9B8E, 0xFF3CC97A};
        if (psi <= 100) return new int[]{0xFF2F6FE0, 0xFF56B4F2};
        if (psi <= 200) return new int[]{0xFFE0702A, 0xFFF2C14C};
        if (psi <= 300) return new int[]{0xFFC9233A, 0xFFF06A3C};
        return new int[]{0xFF4A0E1E, 0xFF8E2042};
    }

    static int widgetBackground(Integer psi) {
        if (psi == null) return R.drawable.widget_bg_none;
        if (psi <= 50) return R.drawable.widget_bg_good;
        if (psi <= 100) return R.drawable.widget_bg_moderate;
        if (psi <= 200) return R.drawable.widget_bg_unhealthy;
        if (psi <= 300) return R.drawable.widget_bg_very_unhealthy;
        return R.drawable.widget_bg_hazardous;
    }

    /** Short health tip for the general public, following NEA's advisory bands. */
    static String bandAdvice(int psi) {
        if (psi <= 50) return "Great day to be outdoors.";
        if (psi <= 100) return "Air is fine for normal activities.";
        if (psi <= 200) return "Cut down on long or strenuous outdoor activity.";
        if (psi <= 300) return "Avoid long or strenuous outdoor activity.";
        return "Stay indoors and keep outdoor activity to a minimum.";
    }

    /** "2026-10-22 11:00:00" → "11:00". */
    static String shortTime(String timestamp) {
        return timestamp != null && timestamp.length() >= 16 ? timestamp.substring(11, 16) : "";
    }

    static String bandName(int psi) {
        if (psi <= 50) return "Good";
        if (psi <= 100) return "Moderate";
        if (psi <= 200) return "Unhealthy";
        if (psi <= 300) return "Very unhealthy";
        return "Hazardous";
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
}
