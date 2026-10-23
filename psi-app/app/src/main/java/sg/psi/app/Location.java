package sg.psi.app;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** A place the user cares about: a name, an icon and the NEA region it is in. */
final class Location {

    /** Card-order key for the Across Singapore card, which sits among the location cards. */
    static final String REGIONS_CARD = "regions";

    static final String[] ICONS = {
            "🏠", "🏢", "🏫", "🎓", "🏥", "🏋️",
            "🛒", "☕", "🍜", "🌳", "🏖️", "⚽",
            "👵", "👶", "🐶", "❤️", "⭐", "📍",
            "⛪", "🕌", "🛕", "🚉", "✈️", "🏡",
    };

    final String id;
    String name;
    String icon;
    String region;
    /** The address or postal code the region was found from, if any. */
    String address = "";

    Location(String id, String name, String icon, String region) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.region = region;
    }

    /** Saved locations; on first use, Home and Office from the earlier versions of the app. */
    static List<Location> loadAll(SharedPreferences prefs) {
        List<Location> list = new ArrayList<>();
        String json = prefs.getString(PsiClient.PREF_LOCATIONS, null);
        if (json != null) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject o = array.getJSONObject(i);
                    Location location = new Location(o.getString("id"), o.optString("name", "Place"),
                            o.optString("icon", "📍"), o.optString("region", PsiClient.DEFAULT_OFFICE));
                    location.address = o.optString("address", "");
                    list.add(location);
                }
            } catch (JSONException ignored) {
                list.clear();
            }
        }
        if (list.isEmpty()) {
            list.add(new Location("home", "Home", "🏠",
                    prefs.getString(PsiClient.PREF_HOME, PsiClient.DEFAULT_HOME)));
            list.add(new Location("office", "Office", "🏢",
                    prefs.getString(PsiClient.PREF_OFFICE, PsiClient.DEFAULT_OFFICE)));
        }
        return list;
    }

    static void saveAll(SharedPreferences prefs, List<Location> list) {
        JSONArray array = new JSONArray();
        try {
            for (Location l : list) {
                array.put(new JSONObject().put("id", l.id).put("name", l.name)
                        .put("icon", l.icon).put("region", l.region).put("address", l.address));
            }
        } catch (JSONException ignored) {
            return;
        }
        prefs.edit().putString(PsiClient.PREF_LOCATIONS, array.toString()).apply();
    }

    /** Card order: every location id plus the Across Singapore card, each exactly once. */
    static List<String> loadOrder(SharedPreferences prefs, List<Location> locations) {
        List<String> known = new ArrayList<>();
        for (Location l : locations) known.add(l.id);
        known.add(REGIONS_CARD);

        List<String> order = new ArrayList<>();
        for (String key : prefs.getString(PsiClient.PREF_CARD_ORDER, "").split(",")) {
            if (known.contains(key) && !order.contains(key)) order.add(key);
        }
        for (String key : known) {
            if (order.contains(key)) continue;
            int regions = order.indexOf(REGIONS_CARD);
            // New locations go before the Across Singapore card when it is at the end.
            if (!key.equals(REGIONS_CARD) && regions >= 0 && regions == order.size() - 1) order.add(regions, key);
            else order.add(key);
        }
        return order;
    }

    static void saveOrder(SharedPreferences prefs, List<String> order) {
        prefs.edit().putString(PsiClient.PREF_CARD_ORDER, TextUtils.join(",", order)).apply();
    }

    /** Locations in the order their cards appear. */
    static List<Location> ordered(SharedPreferences prefs) {
        List<Location> all = loadAll(prefs);
        List<Location> result = new ArrayList<>();
        for (String key : loadOrder(prefs, all)) {
            for (Location l : all) {
                if (l.id.equals(key)) result.add(l);
            }
        }
        return result;
    }
}
