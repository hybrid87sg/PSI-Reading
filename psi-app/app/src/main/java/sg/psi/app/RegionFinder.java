package sg.psi.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Works out which NEA region a place is in, from GPS coordinates, a postal code or an address. */
final class RegionFinder {

    /** A place that was found and the region it is in. */
    static final class Match {
        final String region;
        final String address;

        Match(String region, String address) {
            this.region = region;
            this.address = address;
        }
    }

    // NEA doesn't publish region borders, so a place goes to the region with the nearest centre.
    // Each centre is the middle of the towns the app lists for that region (PsiClient.REGION_AREAS),
    // so lookups agree with those hints.
    private static final double[][] CENTRES = {
            {1.435, 103.810},  // north: Woodlands, Yishun, Sembawang
            {1.275, 103.830},  // south: Bukit Merah, Telok Blangah, Sentosa, the city
            {1.352, 103.950},  // east: Tampines, Bedok, Pasir Ris, Changi
            {1.347, 103.740},  // west: Jurong, Clementi, Bukit Batok, Choa Chu Kang
            {1.327, 103.845},  // central: Toa Payoh, Bishan, Orchard, Novena
    };

    // Rough centre of each postal district (1–28), used when OneMap can't be reached.
    private static final double[][] DISTRICT_CENTRES = {
            {1.283, 103.851}, {1.276, 103.845}, {1.291, 103.807}, {1.265, 103.822}, {1.304, 103.765},
            {1.291, 103.850}, {1.302, 103.858}, {1.310, 103.854}, {1.304, 103.832}, {1.318, 103.806},
            {1.321, 103.843}, {1.330, 103.854}, {1.335, 103.880}, {1.318, 103.890}, {1.306, 103.905},
            {1.324, 103.930}, {1.358, 103.987}, {1.354, 103.945}, {1.372, 103.894}, {1.360, 103.845},
            {1.343, 103.776}, {1.338, 103.706}, {1.375, 103.763}, {1.410, 103.720}, {1.436, 103.786},
            {1.391, 103.822}, {1.430, 103.835}, {1.399, 103.868},
    };

    // Postal sector (first two digits) → postal district.
    private static final int[] SECTOR_TO_DISTRICT = new int[83];

    static {
        int[][] districts = {
                {1, 1, 2, 3, 4, 5, 6}, {2, 7, 8}, {3, 14, 15, 16}, {4, 9, 10}, {5, 11, 12, 13}, {6, 17},
                {7, 18, 19}, {8, 20, 21}, {9, 22, 23}, {10, 24, 25, 26, 27}, {11, 28, 29, 30},
                {12, 31, 32, 33}, {13, 34, 35, 36, 37}, {14, 38, 39, 40, 41}, {15, 42, 43, 44, 45},
                {16, 46, 47, 48}, {17, 49, 50, 81}, {18, 51, 52}, {19, 53, 54, 55, 82}, {20, 56, 57},
                {21, 58, 59}, {22, 60, 61, 62, 63, 64}, {23, 65, 66, 67, 68}, {24, 69, 70, 71},
                {25, 72, 73}, {26, 77, 78}, {27, 75, 76}, {28, 79, 80},
        };
        for (int[] d : districts) {
            for (int i = 1; i < d.length; i++) SECTOR_TO_DISTRICT[d[i]] = d[0];
        }
    }

    private RegionFinder() {
    }

    static boolean inSingapore(double lat, double lon) {
        return lat > 1.15 && lat < 1.48 && lon > 103.59 && lon < 104.1;
    }

    static String nearestRegion(double lat, double lon) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < CENTRES.length; i++) {
            double dLat = lat - CENTRES[i][0];
            double dLon = lon - CENTRES[i][1];
            double distance = dLat * dLat + dLon * dLon;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return PsiClient.REGIONS[best];
    }

    /**
     * Looks up a postal code or address on OneMap, Singapore's official map service. If OneMap
     * can't be reached, a 6-digit postal code still works using its postal district.
     * Runs network I/O, so call it off the main thread. Returns null when nothing is found.
     */
    static Match search(String query) {
        String q = query.trim();
        try {
            Match match = searchOneMap(q);
            if (match != null) return match;
        } catch (Exception ignored) {
            // Fall back to the postal district below.
        }
        if (q.matches("\\d{6}")) {
            int sector = Integer.parseInt(q.substring(0, 2));
            int district = sector < SECTOR_TO_DISTRICT.length ? SECTOR_TO_DISTRICT[sector] : 0;
            if (district > 0) {
                double[] centre = DISTRICT_CENTRES[district - 1];
                return new Match(nearestRegion(centre[0], centre[1]), "Singapore " + q);
            }
        }
        return null;
    }

    private static Match searchOneMap(String query) throws Exception {
        URL url = new URL("https://www.onemap.gov.sg/api/common/elastic/search?searchVal="
                + URLEncoder.encode(query, "UTF-8") + "&returnGeom=Y&getAddrDetails=Y&pageNum=1");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        try {
            if (conn.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            JSONArray results = new JSONObject(sb.toString()).optJSONArray("results");
            if (results == null || results.length() == 0) return null;
            JSONObject top = results.getJSONObject(0);
            double lat = Double.parseDouble(top.getString("LATITUDE"));
            double lon = Double.parseDouble(top.getString("LONGITUDE"));
            if (!inSingapore(lat, lon)) return null;
            return new Match(nearestRegion(lat, lon), titleCase(top.optString("ADDRESS", query)));
        } finally {
            conn.disconnect();
        }
    }

    /** "123 TAMPINES STREET 11 SINGAPORE 521123" → "123 Tampines Street 11 Singapore 521123". */
    private static String titleCase(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        boolean start = true;
        for (char ch : text.toCharArray()) {
            sb.append(start ? Character.toUpperCase(ch) : Character.toLowerCase(ch));
            start = Character.isWhitespace(ch) || ch == '(' || ch == '/';
        }
        return sb.toString();
    }
}
