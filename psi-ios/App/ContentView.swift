import SwiftUI
import UniformTypeIdentifiers
import WidgetKit

/// What the single sheet on the main screen is showing.
enum Sheet: Identifiable {
    case pickRegion(String)
    case edit(String)
    case add

    var id: String {
        switch self {
        case .pickRegion(let id): return "region-\(id)"
        case .edit(let id): return "edit-\(id)"
        case .add: return "add"
        }
    }
}

struct ContentView: View {
    /// Screens at least this wide (iPad, landscape) fit more cards per row.
    private static let wideWidth: CGFloat = 600

    // Home and Office from the first version; read once to seed the location list.
    @AppStorage("home_region") private var homeRaw = Region.east.rawValue
    @AppStorage("office_region") private var officeRaw = Region.central.rawValue
    @AppStorage("locations") private var locationsRaw = ""
    @AppStorage("card_order") private var orderRaw = "home,office,regions"
    @AppStorage("setup_done") private var setupDone = false

    @Environment(\.scenePhase) private var scenePhase
    @State private var reading: PSIReading? = PSIClient.loadCached()
    @State private var status = "Loading…"
    @State private var spin = 0.0
    @State private var sheet: Sheet?
    @State private var settingUp = false
    @State private var dragging: String?

    private var locations: [Location] {
        get {
            Location.decode(locationsRaw,
                            home: Region(rawValue: homeRaw) ?? .east,
                            office: Region(rawValue: officeRaw) ?? .central)
        }
        nonmutating set { locationsRaw = Location.encode(newValue) }
    }

    /// The saved card order: every location id plus the Across Singapore card.
    private var order: Binding<[String]> {
        Binding(
            get: { Location.order(orderRaw, locations: locations) },
            set: { orderRaw = $0.joined(separator: ",") }
        )
    }

    /// The screen takes on the colour of the worst reading among the locations.
    private var worst: Int? {
        locations.compactMap { reading?.value($0.region) }.max()
    }

    var body: some View {
        GeometryReader { geo in
            let wide = geo.size.width >= Self.wideWidth
            // Uses the largest card size that fits on one screen; scrolls only if even the
            // smallest doesn't fit.
            ViewThatFits(in: .vertical) {
                page(.large, wide: wide)
                page(.medium, wide: wide)
                page(.small, wide: wide)
                page(.tiny, wide: wide)
                ScrollView { page(.mini, wide: wide) }
            }
            .frame(maxHeight: .infinity, alignment: .top)
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
                if locations.contains(where: { $0.id == "home" }) {
                    settingUp = true
                    sheet = .pickRegion("home")
                }
            }
        }
        .sheet(item: $sheet) { item in
            switch item {
            case .pickRegion(let id):
                if let location = locations.first(where: { $0.id == id }) {
                    RegionPicker(title: regionTitle(location), selected: location.region) { region in
                        update(id) { $0.region = region }
                        sheet = nil
                        if settingUp && id == "home" && locations.contains(where: { $0.id == "office" }) {
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { sheet = .pickRegion("office") }
                        } else {
                            settingUp = false
                        }
                    }
                }
            case .edit(let id):
                if let location = locations.first(where: { $0.id == id }) {
                    LocationEditor(location: location, canDelete: locations.count > 1,
                                   save: { edited in
                                       // An emptied name keeps the old one.
                                       update(id) { current in
                                           let name = edited.name.isEmpty ? current.name : edited.name
                                           current = edited
                                           current.name = name
                                       }
                                       sheet = nil
                                   },
                                   delete: { remove(id); sheet = nil },
                                   cancel: { sheet = nil })
                }
            case .add:
                LocationEditor(location: Location(id: "loc\(Int(Date().timeIntervalSince1970 * 1000))",
                                                  name: "", icon: "📍", region: .central),
                               canDelete: false,
                               save: { added in add(added); sheet = nil },
                               delete: {},
                               cancel: { sheet = nil })
            }
        }
    }

    private func page(_ size: CardSize, wide: Bool) -> some View {
        VStack(alignment: .leading, spacing: size.gap) {
            header
            Text(status)
                .font(.footnote)
                .padding(.horizontal, 12)
                .padding(.vertical, 5)
                .background(Capsule().fill(.white.opacity(0.18)))
            cards(size, wide: wide)
            Text("Tap ＋ to add a location · Hold and drag a card to move it\n24-hour PSI · National Environment Agency via data.gov.sg")
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
                ForEach(locations) { location in
                    Button("\(location.icon) \(location.name) · \(location.region.label)") {
                        sheet = .edit(location.id)
                    }
                }
                Divider()
                Button {
                    sheet = .add
                } label: {
                    Label(locations.count < Location.maxCount
                          ? "Add a location" : "Up to \(Location.maxCount) locations",
                          systemImage: "plus")
                }
                .disabled(locations.count >= Location.maxCount)
            } label: {
                roundIcon("plus")
            }
            .accessibilityLabel("Add or edit locations")
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

    /// Cards in the saved order, several per row when there is room.
    /// Across Singapore always gets a full row of its own.
    private func cards(_ size: CardSize, wide: Bool) -> some View {
        let columns = wide ? (size.compact ? 3 : 2) : (size.compact ? 2 : 1)
        var rows: [[String]] = []
        var row: [String] = []
        for key in order.wrappedValue {
            if key == Location.regionsCard {
                if !row.isEmpty { rows.append(row); row = [] }
                rows.append([key])
                continue
            }
            row.append(key)
            if row.count == columns { rows.append(row); row = [] }
        }
        if !row.isEmpty { rows.append(row) }

        return VStack(spacing: size.gap) {
            ForEach(rows, id: \.self) { row in
                HStack(alignment: .top, spacing: size.gap) {
                    ForEach(row, id: \.self) { key in
                        card(key, size: size)
                    }
                    // Empty slots keep a short last row's cards the same width as the others.
                    if row != [Location.regionsCard] {
                        ForEach(0..<(columns - row.count), id: \.self) { _ in
                            Color.clear.frame(maxWidth: .infinity, maxHeight: 0)
                        }
                    }
                }
                .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func card(_ key: String, size: CardSize) -> some View {
        Group {
            if key == Location.regionsCard {
                RegionsCard(reading: reading, locations: locations, size: size)
            } else if let location = locations.first(where: { $0.id == key }) {
                LocationCard(location: location, psi: reading?.value(location.region), size: size) {
                    sheet = .pickRegion(location.id)
                }
                .contentShape(Rectangle())
                .onTapGesture { sheet = .edit(location.id) }
            }
        }
        .padding(size.padding)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(Color.cardBase)
                .shadow(color: .black.opacity(0.18), radius: 10, y: 4)
        )
        .opacity(dragging == key ? 0.4 : 1)
        .onDrag {
            dragging = key
            return NSItemProvider(object: key as NSString)
        }
        .onDrop(of: [UTType.text], delegate: ReorderDelegate(target: key, order: order, dragging: $dragging))
    }

    // ---- Changing locations ----

    private func update(_ id: String, _ change: (inout Location) -> Void) {
        var all = locations
        guard let index = all.firstIndex(where: { $0.id == id }) else { return }
        change(&all[index])
        locations = all
        WidgetCenter.shared.reloadAllTimelines()
    }

    private func add(_ location: Location) {
        var added = location
        if added.name.isEmpty { added.name = "Place \(locations.count + 1)" }
        var keys = order.wrappedValue
        locations = locations + [added]
        // New cards go after the last location card.
        let at = (keys.lastIndex(where: { $0 != Location.regionsCard }) ?? -1) + 1
        keys.insert(added.id, at: at)
        order.wrappedValue = keys
    }

    private func remove(_ id: String) {
        locations = locations.filter { $0.id != id }
        order.wrappedValue = order.wrappedValue.filter { $0 != id }
    }

    private func regionTitle(_ location: Location) -> String {
        location.id == "home" || location.id == "office"
            ? "Where is your \(location.name.lowercased())?"
            : "Which region is \(location.name) in?"
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
    let target: String
    let order: Binding<[String]>
    @Binding var dragging: String?

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

/// Add or edit a location: name, icon and region; existing ones can also be deleted.
struct LocationEditor: View {
    @State var location: Location
    let canDelete: Bool
    let save: (Location) -> Void
    let delete: () -> Void
    let cancel: () -> Void
    @State private var confirmingDelete = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Name") {
                    TextField("e.g. Gym, Mum's place", text: $location.name)
                        .textInputAutocapitalization(.words)
                        .onChange(of: location.name) { _, name in
                            if name.count > 20 { location.name = String(name.prefix(20)) }
                        }
                }
                Section("Icon") {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 6), spacing: 8) {
                        ForEach(Location.icons, id: \.self) { icon in
                            Text(icon)
                                .font(.title2)
                                .frame(width: 44, height: 44)
                                .background(Circle().fill(icon == location.icon ? Color.cardRaised : .clear))
                                .overlay(Circle().strokeBorder(Color.cardMuted, lineWidth: icon == location.icon ? 2 : 0))
                                .onTapGesture { location.icon = icon }
                                .accessibilityAddTraits(icon == location.icon ? .isSelected : [])
                        }
                    }
                    .padding(.vertical, 4)
                }
                Section("Region") {
                    ForEach(Region.allCases) { region in
                        Button {
                            location.region = region
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(region.label).font(.headline)
                                    Text(region.areas).font(.subheadline).foregroundStyle(.secondary)
                                }
                                Spacer()
                                if region == location.region {
                                    Image(systemName: "checkmark").foregroundStyle(Color.cardMuted)
                                }
                            }
                            .contentShape(Rectangle())
                        }
                        .foregroundStyle(.primary)
                    }
                }
                if canDelete {
                    Section {
                        Button("Delete location", role: .destructive) { confirmingDelete = true }
                    }
                }
            }
            .navigationTitle(canDelete ? "Edit location" : "Add a location")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel", action: cancel) }
                ToolbarItem(placement: .confirmationAction) {
                    Button(canDelete ? "Save" : "Add") {
                        location.name = location.name.trimmingCharacters(in: .whitespaces)
                        save(location)
                    }
                }
            }
            .confirmationDialog("Delete \(location.name)?", isPresented: $confirmingDelete, titleVisibility: .visible) {
                Button("Delete", role: .destructive, action: delete)
            } message: {
                Text("Its card will be removed. You can add it again later.")
            }
        }
    }
}
