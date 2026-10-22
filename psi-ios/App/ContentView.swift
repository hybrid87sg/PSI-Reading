import SwiftUI
import UniformTypeIdentifiers
import WidgetKit

enum CardKind: String, CaseIterable, Identifiable {
    case home, office, regions
    var id: String { rawValue }
}

enum Place: String, Identifiable {
    case home, office
    var id: String { rawValue }
    var title: String { self == .home ? "Home" : "Office" }
    var emoji: String { self == .home ? "🏠" : "🏢" }
}

struct ContentView: View {
    /// Screens at least this wide (iPad, landscape) show two cards per row.
    private static let wideWidth: CGFloat = 600

    @AppStorage("home_region") private var homeRaw = Region.east.rawValue
    @AppStorage("office_region") private var officeRaw = Region.central.rawValue
    @AppStorage("card_order") private var orderRaw = "home,office,regions"
    @AppStorage("setup_done") private var setupDone = false

    @Environment(\.scenePhase) private var scenePhase
    @State private var reading: PSIReading? = PSIClient.loadCached()
    @State private var status = "Loading…"
    @State private var spin = 0.0
    @State private var picking: Place?
    @State private var settingUp = false
    @State private var dragging: CardKind?

    private var home: Region { Region(rawValue: homeRaw) ?? .east }
    private var office: Region { Region(rawValue: officeRaw) ?? .central }

    /// The saved card order, always containing each card exactly once.
    private var order: Binding<[CardKind]> {
        Binding(
            get: {
                var result: [CardKind] = []
                for key in orderRaw.split(separator: ",") {
                    if let kind = CardKind(rawValue: String(key)), !result.contains(kind) { result.append(kind) }
                }
                for kind in CardKind.allCases where !result.contains(kind) { result.append(kind) }
                return result
            },
            set: { orderRaw = $0.map(\.rawValue).joined(separator: ",") }
        )
    }

    /// The screen takes on the colour of the worse of the two readings.
    private var worst: Int? {
        [reading?.value(home), reading?.value(office)].compactMap { $0 }.max()
    }

    var body: some View {
        GeometryReader { geo in
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header
                    Text(status)
                        .font(.footnote)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 5)
                        .background(Capsule().fill(.white.opacity(0.18)))
                    cards(wide: geo.size.width >= Self.wideWidth)
                    Text("Hold and drag a card to move it\n24-hour PSI · National Environment Agency via data.gov.sg")
                        .font(.caption)
                        .multilineTextAlignment(.center)
                        .opacity(0.75)
                        .frame(maxWidth: .infinity)
                }
                .padding(.horizontal, 20)
                .padding(.top, 12)
                .padding(.bottom, 24)
                .frame(maxWidth: 840)
                .frame(maxWidth: .infinity)
                // A drop that misses every card still ends the drag.
                .onDrop(of: [UTType.text], isTargeted: nil) { _ in
                    dragging = nil
                    return true
                }
            }
        }
        .foregroundStyle(.white)
        .background(
            LinearGradient(colors: Band.of(worst).gradient, startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()
                .animation(.easeInOut, value: worst)
        )
        .preferredColorScheme(.dark)
        .task { await refresh() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { Task { await refresh() } }
        }
        .onAppear {
            // First launch: ask where home and office are.
            if !setupDone {
                setupDone = true
                settingUp = true
                picking = .home
            }
        }
        .sheet(item: $picking) { place in
            RegionPicker(place: place, selected: place == .home ? home : office) { region in
                if place == .home { homeRaw = region.rawValue } else { officeRaw = region.rawValue }
                WidgetCenter.shared.reloadAllTimelines()
                picking = nil
                if place == .home && settingUp {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { picking = .office }
                } else {
                    settingUp = false
                }
            }
        }
    }

    private var header: some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(greeting).font(.system(size: 28, weight: .medium))
                Text(Date.now.formatted(.dateTime.weekday(.wide).day().month(.wide)))
                    .font(.subheadline)
                    .opacity(0.85)
            }
            Spacer()
            Menu {
                Button("🏠 Home · \(home.label)") { picking = .home }
                Button("🏢 Office · \(office.label)") { picking = .office }
            } label: {
                roundIcon("location.fill")
            }
            .accessibilityLabel("Set home and office regions")
            Button {
                Task { await refresh() }
            } label: {
                roundIcon("arrow.clockwise").rotationEffect(.degrees(spin))
            }
            .accessibilityLabel("Refresh")
        }
    }

    private func roundIcon(_ name: String) -> some View {
        Image(systemName: name)
            .font(.system(size: 18, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: 48, height: 48)
            .background(Circle().fill(.white.opacity(0.2)))
    }

    /// Cards in the saved order: one column, or two per row on wide screens.
    private func cards(wide: Bool) -> some View {
        let kinds = order.wrappedValue
        let rows: [[CardKind]] = wide
            ? stride(from: 0, to: kinds.count, by: 2).map { Array(kinds[$0..<min($0 + 2, kinds.count)]) }
            : kinds.map { [$0] }
        return VStack(spacing: 16) {
            ForEach(rows, id: \.self) { row in
                HStack(alignment: .top, spacing: 16) {
                    ForEach(row) { kind in
                        card(kind)
                            .padding(20)
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                            .background(
                                RoundedRectangle(cornerRadius: 28, style: .continuous)
                                    .fill(Color.cardBase)
                                    .shadow(color: .black.opacity(0.18), radius: 10, y: 4)
                            )
                            .opacity(dragging == kind ? 0.4 : 1)
                            .onDrag {
                                dragging = kind
                                return NSItemProvider(object: kind.rawValue as NSString)
                            }
                            .onDrop(of: [UTType.text],
                                    delegate: ReorderDelegate(target: kind, order: order, dragging: $dragging))
                    }
                }
                .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    @ViewBuilder
    private func card(_ kind: CardKind) -> some View {
        switch kind {
        case .home:
            PlaceCard(place: .home, region: home, psi: reading?.value(home)) { picking = .home }
        case .office:
            PlaceCard(place: .office, region: office, psi: reading?.value(office)) { picking = .office }
        case .regions:
            RegionsCard(reading: reading, home: home, office: office)
        }
    }

    private var greeting: String {
        let hour = Calendar.current.component(.hour, from: .now)
        if hour < 12 { return "Good morning" }
        if hour < 18 { return "Good afternoon" }
        return "Good evening"
    }

    private func refresh() async {
        status = "Updating…"
        withAnimation(.easeInOut(duration: 0.7)) { spin += 360 }
        do {
            let latest = try await PSIClient.fetchLatest()
            PSIClient.save(latest)
            reading = latest
            status = latest.shortTime.isEmpty ? "Updated just now" : "NEA update at \(latest.shortTime)"
            WidgetCenter.shared.reloadAllTimelines()
        } catch {
            status = reading == nil ? "Couldn't reach NEA · tap ↻ to try again" : "Offline · showing the last reading"
        }
    }
}

/// Moves the dragged card into the slot of the card it is dragged over.
struct ReorderDelegate: DropDelegate {
    let target: CardKind
    let order: Binding<[CardKind]>
    @Binding var dragging: CardKind?

    func dropEntered(info: DropInfo) {
        guard let dragging, dragging != target,
              let from = order.wrappedValue.firstIndex(of: dragging),
              let to = order.wrappedValue.firstIndex(of: target) else { return }
        withAnimation(.easeInOut(duration: 0.2)) {
            order.wrappedValue.move(fromOffsets: IndexSet(integer: from), toOffset: to > from ? to + 1 : to)
        }
    }

    func dropUpdated(info: DropInfo) -> DropProposal? { DropProposal(operation: .move) }

    func performDrop(info: DropInfo) -> Bool {
        dragging = nil
        return true
    }
}
