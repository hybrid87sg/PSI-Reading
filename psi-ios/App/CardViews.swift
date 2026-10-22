import SwiftUI

/// The Home or Office card: region chip, ring gauge, band and health tip.
struct PlaceCard: View {
    let place: Place
    let region: Region
    let psi: Int?
    let pick: () -> Void

    var body: some View {
        let band = Band.of(psi)
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Text(place.emoji).font(.title2)
                Text(place.title).font(.system(size: 19, weight: .medium))
                Spacer()
                Button(action: pick) {
                    HStack(spacing: 6) {
                        Text(region.label)
                        Image(systemName: "chevron.down").font(.caption2.weight(.bold))
                    }
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(Capsule().fill(Color.cardRaised))
                }
                .accessibilityLabel("\(place.title) region: \(region.label). Change")
            }
            HStack(spacing: 18) {
                GaugeView(psi: psi).frame(width: 128, height: 128)
                VStack(alignment: .leading, spacing: 10) {
                    Text(band.name)
                        .font(.subheadline.weight(.medium))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 5)
                        .background(Capsule().fill(band.color))
                    Text(band.advice)
                        .foregroundStyle(Color.cardMuted)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }
}

/// A 270° ring that fills up to the PSI value (scale 0–300) in the band colour.
struct GaugeView: View {
    let psi: Int?
    @State private var progress: CGFloat = 0

    var body: some View {
        let fraction = CGFloat(min(psi ?? 0, 300)) / 300
        ZStack {
            arc(to: 0.75)
                .stroke(Color.cardRaised, style: StrokeStyle(lineWidth: 12.8, lineCap: .round))
            if let psi {
                arc(to: 0.75 * fraction * progress)
                    .stroke(Band.of(psi).color, style: StrokeStyle(lineWidth: 12.8, lineCap: .round))
            }
            VStack(spacing: 4) {
                Text(psi.map(String.init) ?? "–")
                    .font(.system(size: 38, weight: .bold))
                    .contentTransition(.numericText())
                Text("PSI")
                    .font(.system(size: 11.5, weight: .medium))
                    .kerning(1.7)
                    .foregroundStyle(Color.cardFaint)
            }
            .offset(y: 6)
        }
        .padding(6.4)
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

/// All five regions as coloured circles, with home and office outlined.
struct RegionsCard: View {
    let reading: PSIReading?
    let home: Region
    let office: Region

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Across Singapore").font(.system(size: 17, weight: .medium))
            HStack(alignment: .top, spacing: 0) {
                ForEach(Region.allCases) { region in
                    let psi = reading?.value(region)
                    let mine = region == home || region == office
                    VStack(spacing: 6) {
                        Text(psi.map(String.init) ?? "–")
                            .font(.subheadline.bold())
                            .frame(width: 46, height: 46)
                            .background(Circle().fill(psi.map { Band.of($0).color } ?? Color.cardEmpty))
                            .overlay(Circle().strokeBorder(.white, lineWidth: mine ? 3 : 0))
                        Text(region.label)
                            .font(.caption)
                            .foregroundStyle(Color.cardMuted)
                        Text(tag(region)).font(.caption)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
    }

    private func tag(_ region: Region) -> String {
        let tag = (region == home ? "🏠" : "") + (region == office ? "🏢" : "")
        return tag.isEmpty ? " " : tag
    }
}

/// Region list with example towns, used for first-run setup and later changes.
struct RegionPicker: View {
    let place: Place
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
            .navigationTitle("Where is your \(place.title.lowercased())?")
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
    }
}
