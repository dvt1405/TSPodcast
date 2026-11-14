import SwiftUI

/// "Vị trí podcast" (Android `PodcastLocationSheet`): up to 3 locations, reordered with the arrows
/// or by dragging (the first one is the primary location), removed (never the last one) or added
/// from the supported list. Every change is saved at once.
///
/// Pushed from Settings, or presented as a sheet by "Sắp xếp" on Home (`showsDone`: a "Xong" button).
struct PodcastLocationView: View {
    let model: PodcastLocationsModel
    var showsDone = false

    @Environment(\.dismiss) private var dismiss
    @Environment(\.locale) private var locale

    var body: some View {
        List {
            Section {
                ForEach(Array(model.locations.enumerated()), id: \.element.id) { index, location in
                    SelectedLocationRow(
                        location: location,
                        index: index,
                        count: model.locations.count,
                        canRemove: model.canRemove,
                        locale: locale,
                        move: { offset in Task { await model.move(at: index, by: offset) } },
                        remove: { Task { await model.remove(location) } }
                    )
                }
                .onMove { source, destination in
                    Task { await model.move(fromOffsets: source, toOffset: destination) }
                }
            } footer: {
                Text("location.sheet.hint")
            }

            Section {
                ForEach(model.others) { location in
                    Button {
                        Task { await model.add(location) }
                    } label: {
                        HStack(spacing: 12) {
                            Text(verbatim: location.flag)
                                .font(.title2)
                                .accessibilityHidden(true)
                            LocationNames(location: location, locale: locale, detail: location.languageName)
                            Image(systemName: "plus.circle.fill")
                                .font(.title3)
                                .foregroundStyle(Color.brandPrimary)
                                .accessibilityHidden(true)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .disabled(model.isFull)
                    .opacity(model.isFull ? 0.4 : 1)
                    .accessibilityElement(children: .combine)
                    .accessibilityAddTraits(.isButton)
                    .accessibilityHint(Text("location.add"))
                    .accessibilityIdentifier("location.add.\(location.region)")
                }
            } header: {
                Text(model.isFull ? "location.limitReached" : "location.add")
                    .accessibilityIdentifier("location.add.header")
            }
        }
        .navigationTitle(Text("location.sheet.title"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if showsDone {
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        dismiss()
                    } label: {
                        Text("location.done")
                    }
                    .accessibilityIdentifier("location.done")
                }
            }
        }
        .task { await model.load() }
        .accessibilityIdentifier("location.root")
    }
}

/// A chosen location: its rank (1 = primary), flag, names, ↑ ↓ and remove.
private struct SelectedLocationRow: View {
    let location: PodcastLocationItem
    let index: Int
    let count: Int
    let canRemove: Bool
    let locale: Locale
    let move: (Int) -> Void
    let remove: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            Text(verbatim: "\(index + 1)")
                .font(.subheadline.weight(.bold).monospacedDigit())
                .foregroundStyle(.white)
                .frame(width: 26, height: 26)
                .background(Color.brandPrimary, in: Circle())
                .accessibilityHidden(true)
            Text(verbatim: location.flag)
                .font(.title2)
                .accessibilityHidden(true)
            LocationNames(
                location: location,
                locale: locale,
                detail: location.languageName,
                isPrimary: index == 0
            )
            Button { move(-1) } label: {
                Image(systemName: "chevron.up")
                    .frame(width: 36, height: 44)
            }
            .disabled(index == 0)
            .accessibilityLabel(Text("location.moveUp"))
            .accessibilityIdentifier("location.moveUp.\(location.region)")
            Button { move(1) } label: {
                Image(systemName: "chevron.down")
                    .frame(width: 36, height: 44)
            }
            .disabled(index >= count - 1)
            .accessibilityLabel(Text("location.moveDown"))
            .accessibilityIdentifier("location.moveDown.\(location.region)")
            Button(action: remove) {
                Image(systemName: "xmark.circle.fill")
                    .symbolRenderingMode(.hierarchical)
                    .font(.title3)
                    .frame(width: 36, height: 44)
            }
            .disabled(!canRemove)
            .accessibilityLabel(Text("location.remove"))
            .accessibilityIdentifier("location.remove.\(location.region)")
        }
        // Each control is its own button inside the row, not the whole row.
        .buttonStyle(.borderless)
        .tint(Color.brandPrimary)
        .listRowBackground(index == 0 ? Color.brandPrimary.opacity(0.12) : nil)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("location.selected.\(index)")
    }
}

/// Country name over "language · Chính".
private struct LocationNames: View {
    let location: PodcastLocationItem
    let locale: Locale
    let detail: String
    var isPrimary = false

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(verbatim: location.name(in: locale))
                .font(.body)
                .foregroundStyle(.primary)
                .lineLimit(1)
            Text(verbatim: isPrimary ? "\(detail) · \(String(localized: "location.primary"))" : detail)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
