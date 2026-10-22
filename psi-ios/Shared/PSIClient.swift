import Foundation
import SwiftUI

/// The five regions NEA reports PSI for.
enum Region: String, CaseIterable, Identifiable, Codable {
    case north, south, east, west, central

    var id: String { rawValue }
    var label: String { rawValue.capitalized }

    /// Example towns in each region, to help people pick theirs.
    var areas: String {
        switch self {
        case .north: return "Woodlands, Yishun, Sembawang"
        case .south: return "Bukit Merah, Telok Blangah, Sentosa"
        case .east: return "Tampines, Bedok, Pasir Ris, Changi"
        case .west: return "Jurong, Clementi, Bukit Batok, Choa Chu Kang"
        case .central: return "Toa Payoh, Bishan, Orchard, Novena"
        }
    }
}

/// NEA's PSI bands with the app's colours and a short health tip for the general public.
struct Band {
    let name: String
    let color: Color
    let gradient: [Color]
    let advice: String

    static func of(_ psi: Int?) -> Band {
        guard let psi else {
            return Band(name: "No reading yet", color: .cardEmpty,
                        gradient: [Color(hex: 0x3A4A7A), Color(hex: 0x182848)],
                        advice: "Waiting for the latest NEA update.")
        }
        switch psi {
        case ...50:
            return Band(name: "Good", color: Color(hex: 0x1FA463),
                        gradient: [Color(hex: 0x0F9B8E), Color(hex: 0x3CC97A)],
                        advice: "Great day to be outdoors.")
        case ...100:
            return Band(name: "Moderate", color: Color(hex: 0x2F80ED),
                        gradient: [Color(hex: 0x2F6FE0), Color(hex: 0x56B4F2)],
                        advice: "Air is fine for normal activities.")
        case ...200:
            return Band(name: "Unhealthy", color: Color(hex: 0xF2994A),
                        gradient: [Color(hex: 0xE0702A), Color(hex: 0xF2C14C)],
                        advice: "Cut down on long or strenuous outdoor activity.")
        case ...300:
            return Band(name: "Very unhealthy", color: Color(hex: 0xEB5757),
                        gradient: [Color(hex: 0xC9233A), Color(hex: 0xF06A3C)],
                        advice: "Avoid long or strenuous outdoor activity.")
        default:
            return Band(name: "Hazardous", color: Color(hex: 0x8E2042),
                        gradient: [Color(hex: 0x4A0E1E), Color(hex: 0x8E2042)],
                        advice: "Stay indoors and keep outdoor activity to a minimum.")
        }
    }
}

extension Color {
    init(hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255)
    }

    // Dark turquoise card palette, matching the Android app.
    static let cardBase = Color(hex: 0x0E3B43)
    static let cardRaised = Color(hex: 0x1B535C)
    static let cardMuted = Color(hex: 0xB9D6D9)
    static let cardFaint = Color(hex: 0x8DB5B9)
    static let cardEmpty = Color(hex: 0x3C6B72)
}

/// One set of 24-hr PSI readings, keyed by region.
struct PSIReading: Codable {
    var readings: [String: Int]
    var timestamp: String

    func value(_ region: Region) -> Int? { readings[region.rawValue] }

    /// "2026-10-22T13:15:00+08:00" → "13:15".
    var shortTime: String {
        let chars = Array(timestamp)
        return chars.count >= 16 ? String(chars[11..<16]) : ""
    }
}

enum PSIClient {
    // NEA PSI readings published on data.gov.sg (v2 first, v1 as a fallback).
    private static let urls = [
        "https://api-open.data.gov.sg/v2/real-time/api/psi",
        "https://api.data.gov.sg/v1/environment/psi",
    ].compactMap(URL.init(string:))

    private static let cacheKey = "last_result"

    static func fetchLatest() async throws -> PSIReading {
        var lastError: Error = URLError(.badServerResponse)
        for url in urls {
            do {
                var request = URLRequest(url: url)
                request.timeoutInterval = 10
                let (data, response) = try await URLSession.shared.data(for: request)
                guard (response as? HTTPURLResponse)?.statusCode == 200 else {
                    throw URLError(.badServerResponse)
                }
                return try parse(data)
            } catch {
                lastError = error
            }
        }
        throw lastError
    }

    /// Handles both the v2 shape ({data: {items: [...]}}) and the v1 shape ({items: [...]}).
    static func parse(_ data: Data) throws -> PSIReading {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw URLError(.cannotParseResponse)
        }
        let container = root["data"] as? [String: Any] ?? root
        guard let items = container["items"] as? [[String: Any]],
              let item = items.last,
              let readings = item["readings"] as? [String: Any],
              let psi = readings["psi_twenty_four_hourly"] as? [String: Any] else {
            throw URLError(.cannotParseResponse)
        }
        var values: [String: Int] = [:]
        for region in Region.allCases {
            if let number = psi[region.rawValue] as? NSNumber {
                values[region.rawValue] = number.intValue
            }
        }
        if values.isEmpty { throw URLError(.cannotParseResponse) }
        let timestamp = (item["updatedTimestamp"] ?? item["update_timestamp"] ?? item["timestamp"]) as? String
        return PSIReading(readings: values, timestamp: timestamp ?? "")
    }

    /// Keeps the last good reading so there's something to show when offline.
    static func save(_ reading: PSIReading) {
        if let data = try? JSONEncoder().encode(reading) {
            UserDefaults.standard.set(data, forKey: cacheKey)
        }
    }

    static func loadCached() -> PSIReading? {
        guard let data = UserDefaults.standard.data(forKey: cacheKey) else { return nil }
        return try? JSONDecoder().decode(PSIReading.self, from: data)
    }
}
