import Foundation

/// A place the user cares about: a name, an icon and the NEA region it is in.
struct Location: Codable, Identifiable, Equatable {
    var id: String
    var name: String
    var icon: String
    var region: Region

    static let maxCount = 8

    static let icons = [
        "🏠", "🏢", "🏫", "🎓", "🏥", "🏋️",
        "🛒", "☕", "🍜", "🌳", "🏖️", "⚽",
        "👵", "👶", "🐶", "❤️", "⭐", "📍",
        "⛪", "🕌", "🛕", "🚉", "✈️", "🏡",
    ]

    /// Card-order key for the Across Singapore card, which sits among the location cards.
    static let regionsCard = "regions"

    /// Saved locations; on first use, Home and Office from the earlier version of the app.
    static func decode(_ raw: String, home: Region, office: Region) -> [Location] {
        if let data = raw.data(using: .utf8),
           let saved = try? JSONDecoder().decode([Location].self, from: data), !saved.isEmpty {
            return saved
        }
        return [
            Location(id: "home", name: "Home", icon: "🏠", region: home),
            Location(id: "office", name: "Office", icon: "🏢", region: office),
        ]
    }

    static func encode(_ locations: [Location]) -> String {
        guard let data = try? JSONEncoder().encode(locations) else { return "" }
        return String(decoding: data, as: UTF8.self)
    }

    /// Card order: every location id plus the Across Singapore card, each exactly once.
    static func order(_ raw: String, locations: [Location]) -> [String] {
        let known = locations.map(\.id) + [regionsCard]
        var order: [String] = []
        for key in raw.split(separator: ",").map(String.init) where known.contains(key) && !order.contains(key) {
            order.append(key)
        }
        for key in known where !order.contains(key) {
            // New locations go before the Across Singapore card when it is at the end.
            if key != regionsCard, let regions = order.firstIndex(of: regionsCard), regions == order.count - 1 {
                order.insert(key, at: regions)
            } else {
                order.append(key)
            }
        }
        return order
    }
}

/// Card sizes, largest first. The screen uses the largest one that fits without scrolling;
/// compact sizes stack the gauge above the band and put more cards on each row.
enum CardSize: CaseIterable {
    case large, medium, small, tiny
    /// Small gauge beside the band and name, for many locations on a small screen.
    case mini

    var gauge: CGFloat {
        switch self {
        case .large: return 128
        case .medium: return 104
        case .small: return 96
        case .tiny: return 76
        case .mini: return 56
        }
    }

    var padding: CGFloat {
        switch self {
        case .large: return 20
        case .medium: return 16
        case .small: return 14
        case .tiny: return 12
        case .mini: return 10
        }
    }

    var gap: CGFloat {
        switch self {
        case .large: return 16
        case .medium: return 14
        case .small: return 12
        case .tiny: return 10
        case .mini: return 8
        }
    }

    var compact: Bool { self == .small || self == .tiny || self == .mini }
}
