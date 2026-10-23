import SwiftUI

/// One location's card: icon, name, region chip, ring gauge, band and health tip.
/// Compact sizes stack the gauge above the band and show the region instead of the tip.
struct LocationCard: View {
    let location: Location
    let psi: Int?
    let size: CardSize
    let pickRegion: () -> Void
    /// Sets this location's region from where the phone is now.
    let locate: () -> Void

    var body: some View {
        let band = Band.of(psi)
        if size == .mini {
            // Gauge on the left; band and name on the right; no header row.
            HStack(spacing: 10) {
                GaugeView(psi: psi).frame(width: size.gauge, height: size.gauge)
                VStack(alignment: .leading, spacing: 6) {
                    pill(band, short: true)
                    Text("\(location.icon) \(location.name)")
                        .font(.footnote)
                        .foregroundStyle(Color.cardMuted)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
            }
        } else {
            regularBody(band)
        }
    }

    private func regularBody(_ band: Band) -> some View {
        VStack(alignment: size.compact ? .center : .leading, spacing: size.compact ? 8 : 12) {
            HStack(spacing: 8) {
                Text(location.icon).font(size.compact ? .title3 : .title2)
                // Names may wrap to a second line rather than being cut off.
                Text(location.name)
                    .font(.system(size: size.compact ? 16 : 19, weight: .medium))
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
                if !size.compact {
                    Button(action: pickRegion) {
                        HStack(spacing: 6) {
                            Text(location.region.label)
                            Image(systemName: "chevron.down").font(.caption2.weight(.bold))
                        }
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(Capsule().fill(Color.cardRaised))
                    }
                    .accessibilityLabel("\(location.name) region: \(location.region.label). Change")
                }
                Button(action: locate) {
                    Image(systemName: "location.fill")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.white)
                        .frame(width: 34, height: 34)
                        .background(Circle().fill(Color.cardRaised))
                }
                .accessibilityLabel("Set \(location.name) region from my current location")
            }
            if size.compact {
                GaugeView(psi: psi).frame(width: size.gauge, height: size.gauge)
                VStack(spacing: 4) {
                    pill(band, short: true)
                    Text(location.region.label).font(.footnote).foregroundStyle(Color.cardMuted)
                }
            } else {
                HStack(spacing: 18) {
                    GaugeView(psi: psi).frame(width: size.gauge, height: size.gauge)
                    VStack(alignment: .leading, spacing: 10) {
                        pill(band, short: false)
                        Text(band.advice)
                            .foregroundStyle(Color.cardMuted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: size.compact ? .center : .leading)
    }

    private func pill(_ band: Band, short: Bool) -> some View {
        Text(short && psi == nil ? "No reading" : band.name)
            .font(.subheadline.weight(.medium))
            .lineLimit(1)
            .minimumScaleFactor(0.8)
            .padding(.horizontal, 12)
            .padding(.vertical, 5)
            .background(Capsule().fill(band.color))
    }
}

/// A 270° ring that fills up to the PSI value (scale 0–300) in the band colour.
/// The ring and text scale with the gauge's size.
struct GaugeView: View {
    let psi: Int?
    @State private var progress: CGFloat = 0

    var body: some View {
        GeometryReader { geo in
            let size = min(geo.size.width, geo.size.height)
            let line = size * 0.1
            let fraction = CGFloat(min(psi ?? 0, 300)) / 300
            ZStack {
                arc(to: 0.75)
                    .stroke(Color.cardRaised, style: StrokeStyle(lineWidth: line, lineCap: .round))
                if let psi {
                    arc(to: 0.75 * fraction * progress)
                        .stroke(Band.of(psi).color, style: StrokeStyle(lineWidth: line, lineCap: .round))
                }
                VStack(spacing: size * 0.03) {
                    Text(psi.map(String.init) ?? "–")
                        .font(.system(size: size * 0.3, weight: .bold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                        .contentTransition(.numericText())
                    Text("PSI")
                        .font(.system(size: size * 0.09, weight: .medium))
                        .kerning(size * 0.013)
                        .foregroundStyle(Color.cardFaint)
                }
                .offset(y: size * 0.05)
            }
            .padding(line / 2)
            .frame(width: geo.size.width, height: geo.size.height)
        }
        .onAppear(perform: animate)
        .onChange(of: psi) { _, _ in animate() }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(psi.map { "PSI \($0)" } ?? "No reading")
    }

    private func arc(to end: CGFloat) -> some Shape {
        Circle().trim(from: 0, to: max(end, 0.001)).rotation(.degrees(135))
    }

    private func animate() {
        progress = 0
        DispatchQueue.main.async {
            withAnimation(.easeOut(duration: 0.9)) { progress = 1 }
        }
    }
}

/// All five regions as coloured circles; regions with a saved location are outlined
/// and show that location's icon.
struct RegionsCard: View {
    let reading: PSIReading?
    let locations: [Location]
    let size: CardSize

    var body: some View {
        let dot: CGFloat = size.compact ? 38 : 46
        VStack(alignment: .leading, spacing: size.compact ? 10 : 14) {
            Text("Across Singapore").font(.system(size: 17, weight: .medium))
            HStack(alignment: .top, spacing: 0) {
                ForEach(Region.allCases) { region in
                    let psi = reading?.value(region)
                    let icons = locations.filter { $0.region == region }.prefix(3).map(\.icon).joined()
                    VStack(spacing: 6) {
                        Text(psi.map(String.init) ?? "–")
                            .font(.subheadline.bold())
                            .frame(width: dot, height: dot)
                            .background(Circle().fill(psi.map { Band.of($0).color } ?? Color.cardEmpty))
                            .overlay(Circle().strokeBorder(.white, lineWidth: icons.isEmpty ? 0 : 3))
                        Text(region.label)
                            .font(.caption)
                            .foregroundStyle(Color.cardMuted)
                        Text(icons.isEmpty ? " " : icons).font(.caption)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
    }
}

/// Region list with example towns, used for first-run setup and later changes.
struct RegionPicker: View {
    let title: String
    let selected: Region
    let pick: (Region) -> Void

    var body: some View {
        NavigationStack {
            List(Region.allCases) { region in
                Button {
                    pick(region)
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(region.label).font(.headline)
                            Text(region.areas).font(.subheadline).foregroundStyle(.secondary)
                        }
                        Spacer()
                        if region == selected {
                            Image(systemName: "checkmark").foregroundStyle(Color.cardMuted)
                        }
                    }
                    .contentShape(Rectangle())
                }
                .foregroundStyle(.primary)
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
    }
}
