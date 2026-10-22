import AppIntents
import SwiftUI
import WidgetKit

extension Region: AppEnum {
    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Region"
    static let caseDisplayRepresentations: [Region: DisplayRepresentation] = [
        .north: "North", .south: "South", .east: "East", .west: "West", .central: "Central",
    ]
}

/// Long-press the widget → Edit Widget to pick the regions it shows.
struct PlacesIntent: WidgetConfigurationIntent {
    static let title: LocalizedStringResource = "Home & Office"
    static let description = IntentDescription("Choose the regions of your home and office.")

    @Parameter(title: "Home", default: .east) var home: Region
    @Parameter(title: "Office", default: .central) var office: Region
}

struct PSIEntry: TimelineEntry {
    let date: Date
    let reading: PSIReading?
    let home: Region
    let office: Region
}

struct Provider: AppIntentTimelineProvider {
    private static let sample = PSIReading(readings: ["east": 42, "central": 48], timestamp: "")

    func placeholder(in context: Context) -> PSIEntry {
        PSIEntry(date: .now, reading: Self.sample, home: .east, office: .central)
    }

    func snapshot(for configuration: PlacesIntent, in context: Context) async -> PSIEntry {
        PSIEntry(date: .now, reading: PSIClient.loadCached() ?? Self.sample,
                 home: configuration.home, office: configuration.office)
    }

    // NEA updates PSI hourly, so ask for a refresh about once an hour.
    func timeline(for configuration: PlacesIntent, in context: Context) async -> Timeline<PSIEntry> {
        var reading = PSIClient.loadCached()
        if let latest = try? await PSIClient.fetchLatest() {
            PSIClient.save(latest)
            reading = latest
        }
        let entry = PSIEntry(date: .now, reading: reading, home: configuration.home, office: configuration.office)
        return Timeline(entries: [entry], policy: .after(.now.addingTimeInterval(3600)))
    }
}

struct PSIWidgetView: View {
    let entry: PSIEntry

    var body: some View {
        let home = entry.reading?.value(entry.home)
        let office = entry.reading?.value(entry.office)
        let worst = [home, office].compactMap { $0 }.max()
        let time = entry.reading?.shortTime ?? ""
        VStack(alignment: .leading, spacing: 6) {
            Text(time.isEmpty ? "24-hr PSI" : "24-hr PSI · \(time)")
                .font(.caption.weight(.medium))
                .opacity(0.85)
            HStack(spacing: 14) {
                column("🏠 Home", entry.home, home)
                Rectangle().fill(.white.opacity(0.3)).frame(width: 1, height: 60)
                column("🏢 Office", entry.office, office)
            }
            .frame(maxHeight: .infinity)
        }
        .foregroundStyle(.white)
        // Like the app, the widget takes on the colour of the worse reading.
        .containerBackground(for: .widget) {
            LinearGradient(colors: Band.of(worst).gradient, startPoint: .topLeading, endPoint: .bottomTrailing)
        }
    }

    private func column(_ name: String, _ region: Region, _ psi: Int?) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("\(name) · \(region.label)").font(.caption).lineLimit(1)
            Text(psi.map(String.init) ?? "–").font(.system(size: 34, weight: .medium))
            Text(psi.map { Band.of($0).name } ?? "").font(.caption2).opacity(0.9)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct PSIWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "PSIWidget", intent: PlacesIntent.self, provider: Provider()) { entry in
            PSIWidgetView(entry: entry)
        }
        .configurationDisplayName("PSI Home & Office")
        .description("24-hour PSI for your home and office.")
        .supportedFamilies([.systemMedium])
    }
}

@main
struct PSIWidgetBundle: WidgetBundle {
    var body: some Widget {
        PSIWidget()
    }
}
