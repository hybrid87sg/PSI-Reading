import CoreLocation
import Foundation

/// Works out which NEA region a place is in, from GPS coordinates, a postal code or an address.
enum RegionFinder {
    /// A place that was found and the region it is in.
    struct Match {
        let region: Region
        let address: String
    }

    // NEA doesn't publish region borders, so a place goes to the region with the nearest centre.
    // Each centre is the middle of the towns the app lists for that region (Region.areas),
    // so lookups agree with those hints.
    private static let centres: [(Region, Double, Double)] = [
        (.north, 1.435, 103.810),    // Woodlands, Yishun, Sembawang
        (.south, 1.275, 103.830),    // Bukit Merah, Telok Blangah, Sentosa, the city
        (.east, 1.352, 103.950),     // Tampines, Bedok, Pasir Ris, Changi
        (.west, 1.347, 103.740),     // Jurong, Clementi, Bukit Batok, Choa Chu Kang
        (.central, 1.327, 103.845),  // Toa Payoh, Bishan, Orchard, Novena
    ]

    // Rough centre of each postal district (1–28), used when OneMap can't be reached.
    private static let districtCentres: [(Double, Double)] = [
        (1.283, 103.851), (1.276, 103.845), (1.291, 103.807), (1.265, 103.822), (1.304, 103.765),
        (1.291, 103.850), (1.302, 103.858), (1.310, 103.854), (1.304, 103.832), (1.318, 103.806),
        (1.321, 103.843), (1.330, 103.854), (1.335, 103.880), (1.318, 103.890), (1.306, 103.905),
        (1.324, 103.930), (1.358, 103.987), (1.354, 103.945), (1.372, 103.894), (1.360, 103.845),
        (1.343, 103.776), (1.338, 103.706), (1.375, 103.763), (1.410, 103.720), (1.436, 103.786),
        (1.391, 103.822), (1.430, 103.835), (1.399, 103.868),
    ]

    // Postal district → its postal sectors (first two digits of the postal code).
    private static let districtSectors: [Int: [Int]] = [
        1: [1, 2, 3, 4, 5, 6], 2: [7, 8], 3: [14, 15, 16], 4: [9, 10], 5: [11, 12, 13], 6: [17],
        7: [18, 19], 8: [20, 21], 9: [22, 23], 10: [24, 25, 26, 27], 11: [28, 29, 30],
        12: [31, 32, 33], 13: [34, 35, 36, 37], 14: [38, 39, 40, 41], 15: [42, 43, 44, 45],
        16: [46, 47, 48], 17: [49, 50, 81], 18: [51, 52], 19: [53, 54, 55, 82], 20: [56, 57],
        21: [58, 59], 22: [60, 61, 62, 63, 64], 23: [65, 66, 67, 68], 24: [69, 70, 71],
        25: [72, 73], 26: [77, 78], 27: [75, 76], 28: [79, 80],
    ]

    static func inSingapore(_ lat: Double, _ lon: Double) -> Bool {
        lat > 1.15 && lat < 1.48 && lon > 103.59 && lon < 104.1
    }

    static func nearestRegion(_ lat: Double, _ lon: Double) -> Region {
        centres.min { a, b in
            pow(lat - a.1, 2) + pow(lon - a.2, 2) < pow(lat - b.1, 2) + pow(lon - b.2, 2)
        }!.0
    }

    /// Looks up a postal code or address on OneMap, Singapore's official map service. If OneMap
    /// can't be reached, a 6-digit postal code still works using its postal district.
    static func search(_ query: String) async -> Match? {
        let q = query.trimmingCharacters(in: .whitespaces)
        if let match = try? await searchOneMap(q) { return match }
        guard q.count == 6, q.allSatisfy(\.isNumber), let sector = Int(q.prefix(2)),
              let district = districtSectors.first(where: { $0.value.contains(sector) })?.key else { return nil }
        let centre = districtCentres[district - 1]
        return Match(region: nearestRegion(centre.0, centre.1), address: "Singapore \(q)")
    }

    private static func searchOneMap(_ query: String) async throws -> Match? {
        var components = URLComponents(string: "https://www.onemap.gov.sg/api/common/elastic/search")!
        components.queryItems = [
            URLQueryItem(name: "searchVal", value: query),
            URLQueryItem(name: "returnGeom", value: "Y"),
            URLQueryItem(name: "getAddrDetails", value: "Y"),
            URLQueryItem(name: "pageNum", value: "1"),
        ]
        var request = URLRequest(url: components.url!)
        request.timeoutInterval = 10
        let (data, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200,
              let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let top = (root["results"] as? [[String: Any]])?.first,
              let lat = Double(top["LATITUDE"] as? String ?? ""),
              let lon = Double(top["LONGITUDE"] as? String ?? ""),
              inSingapore(lat, lon) else { return nil }
        let address = (top["ADDRESS"] as? String ?? query).capitalized
        return Match(region: nearestRegion(lat, lon), address: address)
    }
}

/// Gets the phone's approximate position once, asking for permission the first time.
final class PositionFinder: NSObject, CLLocationManagerDelegate {
    static let shared = PositionFinder()

    enum Failure: LocalizedError {
        case denied, unavailable, outsideSingapore

        var errorDescription: String? {
            switch self {
            case .denied: return "Allow location access in Settings, or search by postal code instead."
            case .unavailable: return "Couldn't get your location. Check that Location Services is on and try again."
            case .outsideSingapore: return "You don't seem to be in Singapore right now."
            }
        }
    }

    private let manager = CLLocationManager()
    private var continuation: CheckedContinuation<CLLocation, Error>?

    override init() {
        super.init()
        manager.delegate = self
        // Region-level accuracy is plenty, and quicker and easier on the battery.
        manager.desiredAccuracy = kCLLocationAccuracyKilometer
    }

    /// The region the phone is in now.
    func currentRegion() async throws -> Region {
        let location = try await current()
        let lat = location.coordinate.latitude
        let lon = location.coordinate.longitude
        guard RegionFinder.inSingapore(lat, lon) else { throw Failure.outsideSingapore }
        return RegionFinder.nearestRegion(lat, lon)
    }

    private func current() async throws -> CLLocation {
        try await withCheckedThrowingContinuation { continuation in
            DispatchQueue.main.async {
                self.continuation?.resume(throwing: Failure.unavailable)
                self.continuation = continuation
                switch self.manager.authorizationStatus {
                case .notDetermined: self.manager.requestWhenInUseAuthorization()
                case .denied, .restricted: self.finish(.failure(Failure.denied))
                default: self.manager.requestLocation()
                }
            }
        }
    }

    private func finish(_ result: Result<CLLocation, Error>) {
        continuation?.resume(with: result)
        continuation = nil
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard continuation != nil else { return }
        switch manager.authorizationStatus {
        case .notDetermined: break
        case .denied, .restricted: finish(.failure(Failure.denied))
        default: manager.requestLocation()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        if let location = locations.last { finish(.success(location)) }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        finish(.failure(Failure.unavailable))
    }
}
