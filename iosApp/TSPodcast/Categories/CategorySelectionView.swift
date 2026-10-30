import SwiftUI

/// Favourite category selection (features-spec §3.2, `SelectFavouriteCategoryScreen`): a grid of
/// icon + name cells, the chosen ones as removable badges above "Lưu và tiếp tục", which is
/// enabled from 3 categories. Shimmer cells while E9 loads.
struct CategorySelectionView: View {
    @State private var model: CategorySelectionModel
    @State private var badgesHeight: CGFloat = 0
    let onSaved: () -> Void

    init(model: CategorySelectionModel, onSaved: @escaping () -> Void) {
        _model = State(initialValue: model)
        self.onSaved = onSaved
    }

    /// Android: `screenWidthDp / 150` fixed columns, i.e. 2 on a regular iPhone.
    private let columns = [GridItem(.adaptive(minimum: 150), spacing: 16)]

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                grid
                    .padding(16)
            }
            .accessibilityIdentifier("category.grid")
        }
        .background(Color(.systemBackground))
        // M5: `safeAreaBar` on iOS 26 (the system scroll edge effect fades the grid under the
        // bar, no background of our own); `safeAreaInset` on `.bar` below, as before.
        .bottomBar {
            saveBar
        }
        .task {
            await model.loadIfNeeded()
        }
    }

    private var header: some View {
        VStack(spacing: 2) {
            Text("category.title")
                .font(.headline)
                .accessibilityAddTraits(.isHeader)
            Text("category.subtitle")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }

    @ViewBuilder
    private var grid: some View {
        LazyVGrid(columns: columns, spacing: 16) {
            switch model.state {
            case .loading:
                ForEach(0..<20, id: \.self) { _ in
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .fill(Color.stroke)
                        .aspectRatio(1, contentMode: .fit)
                        .shimmer()
                }
            case .loaded(let categories):
                ForEach(categories) { category in
                    CategoryCell(category: category, isSelected: model.isSelected(category)) {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            model.toggle(category)
                        }
                    }
                }
            }
        }
    }

    /// Badges of the selection (wrapping, scrollable past ~3 lines) and the save button.
    private var saveBar: some View {
        VStack(spacing: 12) {
            if !model.selected.isEmpty {
                ScrollView {
                    FlowLayout(horizontalSpacing: 4, verticalSpacing: 8) {
                        ForEach(model.selected) { category in
                            SelectedCategoryBadge(category: category) {
                                withAnimation(.easeInOut(duration: 0.2)) {
                                    model.remove(category)
                                }
                            }
                        }
                    }
                    .onGeometryChange(for: CGFloat.self, of: \.size.height) { height in
                        badgesHeight = height
                    }
                }
                .frame(height: min(badgesHeight, 116))
                .scrollBounceBehavior(.basedOnSize)
            }
            Button {
                Task {
                    if await model.save() {
                        onSaved()
                    }
                }
            } label: {
                Text("category.save")
            }
            .buttonStyle(GradientButtonStyle(cornerRadius: 8, fillsWidth: true))
            .disabled(!model.canSave)
            .accessibilityIdentifier("category.save")
        }
        .padding(.horizontal, 16)
        .padding(.top, 12)
        .padding(.bottom, 8)
    }
}

/// Grid cell: 48 pt icon by id, the name under it, a 2 pt stroke and a Primary 10 % fill when
/// selected (Android `CategoryItem`).
private struct CategoryCell: View {
    let category: CategoryItem
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
        Button(action: onTap) {
            VStack(spacing: 10) {
                Spacer(minLength: 0)
                CategoryIcon.image(for: category.id)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 48, height: 48)
                    .accessibilityHidden(true)
                Text(category.name)
                    .font(.subheadline.bold())
                    .foregroundStyle(.primary)
                    .lineLimit(2)
                    .minimumScaleFactor(0.8)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 16)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)
            .aspectRatio(1, contentMode: .fit)
            .background(shape.fill(isSelected ? Color.brandPrimary.opacity(0.1) : Color.clear))
            .overlay(shape.strokeBorder(isSelected ? Color.brandPrimary.opacity(0.6) : Color.stroke, lineWidth: 2))
            .contentShape(shape)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(category.name))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .accessibilityIdentifier("category.cell.\(category.id)")
    }
}

/// Badge of a selected category with its "x" (Android `TSBadge` in `Colors.Secondary`).
private struct SelectedCategoryBadge: View {
    let category: CategoryItem
    let onRemove: () -> Void

    var body: some View {
        HStack(spacing: 2) {
            Text(category.name)
                .font(.subheadline.weight(.medium))
                .lineLimit(1)
                .padding(.leading, 10)
            Button(action: onRemove) {
                Image(systemName: "xmark")
                    .font(.caption.weight(.bold))
                    .frame(width: 28, height: 28)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("category.remove \(category.name)"))
            .accessibilityIdentifier("category.selected.\(category.id).remove")
        }
        .foregroundStyle(.white)
        .background(Capsule().fill(Color.brandSecondary))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("category.selected.\(category.id)")
    }
}
