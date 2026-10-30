import SwiftUI

// The only place (with `Main/MiniPlayerSlot.swift`) that calls iOS 26 Liquid Glass APIs (M5 §3.4).
// Every other view goes through these helpers, so iOS 17/18 get a deliberate Material fallback and
// the deployment target stays 17.0. HIG: glass is for navigation and controls, never for content.

/// How a floating control surface is drawn.
enum GlassSurfaceKind: Equatable, Sendable {
    /// iOS 26 Liquid Glass (`.regular`).
    case glass
    /// iOS 17/18 `.regularMaterial`.
    case material
    /// Reduce Transparency: a solid `secondarySystemBackground`.
    case opaque
}

/// The resolved look of a glass surface; pure so it is unit tested (`GlassSurfaceStyleTests`).
struct GlassSurfaceStyle: Equatable, Sendable {
    var kind: GlassSurfaceKind
    /// Increase Contrast: a 1 pt outline around the surface.
    var showsBorder: Bool

    /// Reduce Transparency wins on every OS (opaque); otherwise iOS 26 → glass, older → Material.
    static func resolve(
        isLiquidGlassAvailable: Bool,
        reduceTransparency: Bool,
        increasedContrast: Bool
    ) -> GlassSurfaceStyle {
        let kind: GlassSurfaceKind
        if reduceTransparency {
            kind = .opaque
        } else if isLiquidGlassAvailable {
            kind = .glass
        } else {
            kind = .material
        }
        return GlassSurfaceStyle(kind: kind, showsBorder: increasedContrast)
    }

    /// Whether this OS has Liquid Glass (iOS 26+).
    static var isLiquidGlassAvailable: Bool {
        if #available(iOS 26, *) {
            return true
        } else {
            return false
        }
    }
}

/// Draws `GlassSurfaceStyle` behind the content, clipped to `shape`.
private struct GlassSurfaceModifier<S: Shape>: ViewModifier {
    let shape: S
    let tint: Color?
    let interactive: Bool

    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @Environment(\.colorSchemeContrast) private var contrast

    func body(content: Content) -> some View {
        let style = GlassSurfaceStyle.resolve(
            isLiquidGlassAvailable: GlassSurfaceStyle.isLiquidGlassAvailable,
            reduceTransparency: reduceTransparency,
            increasedContrast: contrast == .increased
        )
        surface(content, kind: style.kind)
            .overlay {
                if style.showsBorder {
                    shape.stroke(Color.primary.opacity(0.35), lineWidth: 1)
                        .allowsHitTesting(false)
                }
            }
    }

    @ViewBuilder
    private func surface(_ content: Content, kind: GlassSurfaceKind) -> some View {
        switch kind {
        case .glass:
            if #available(iOS 26, *) {
                content.glassEffect(Glass.regular.tint(tint).interactive(interactive), in: shape)
            } else {
                fallback(content, fill: AnyShapeStyle(.regularMaterial))
            }
        case .material:
            fallback(content, fill: AnyShapeStyle(.regularMaterial))
        case .opaque:
            fallback(content, fill: AnyShapeStyle(Color(.secondarySystemBackground)))
        }
    }

    /// Material / solid fill; a tint (e.g. a selected chip) fills the shape like `.borderedProminent`.
    private func fallback(_ content: Content, fill: AnyShapeStyle) -> some View {
        content.background {
            if let tint {
                shape.fill(tint)
            } else {
                shape.fill(fill)
            }
        }
    }
}

extension View {
    /// Liquid Glass on iOS 26, `.regularMaterial` below, solid with Reduce Transparency, outlined
    /// with Increase Contrast. For floating controls only (MiniPlayer, Up next panel, chips).
    func glassSurface(in shape: some Shape, tint: Color? = nil, interactive: Bool = false) -> some View {
        modifier(GlassSurfaceModifier(shape: shape, tint: tint, interactive: interactive))
    }

    /// Secondary button: `.glass` on iOS 26, `.bordered` below.
    @ViewBuilder
    func glassButton() -> some View {
        if #available(iOS 26, *) {
            buttonStyle(.glass)
        } else {
            buttonStyle(.bordered)
        }
    }

    /// Primary button: `.glassProminent` tinted on iOS 26, `.borderedProminent` tinted below.
    @ViewBuilder
    func glassProminentButton(tint: Color) -> some View {
        if #available(iOS 26, *) {
            buttonStyle(.glassProminent).tint(tint)
        } else {
            buttonStyle(.borderedProminent).tint(tint)
        }
    }

    /// Round prominent button (the Player's Play/Pause): tinted interactive glass in `shape` on
    /// iOS 26; below, the plain style over `shape` filled with `fallbackFill` (the M3 look). The
    /// label sets the size: unlike `.glassProminent`, no padding is added, so an 84 pt label is an
    /// 84 pt button on every OS.
    @ViewBuilder
    func glassProminentButton(tint: Color, in shape: some Shape, fallbackFill: Color) -> some View {
        if #available(iOS 26, *) {
            buttonStyle(.plain).glassEffect(Glass.regular.tint(tint).interactive(), in: shape)
        } else {
            buttonStyle(.plain).background { shape.fill(fallbackFill) }
        }
    }

    /// Round icon button: circular interactive glass on iOS 26, the plain M3 glyph below. The
    /// label's frame is the button (no padding added, unlike `.glass`), so a 44/56 pt label keeps
    /// its exact hit target and the fallback row lays out exactly as in M3.
    @ViewBuilder
    func glassCircleButton() -> some View {
        if #available(iOS 26, *) {
            buttonStyle(.plain).glassEffect(Glass.regular.interactive(), in: Circle())
        } else {
            buttonStyle(.plain)
        }
    }

    /// The tab bar shrinks while scrolling down (iOS 26); nothing below.
    @ViewBuilder
    func tabBarMinimizesOnScroll() -> some View {
        if #available(iOS 26, *) {
            tabBarMinimizeBehavior(.onScrollDown)
        } else {
            self
        }
    }

    /// A bar pinned to the bottom: `safeAreaBar` on iOS 26 (system scroll edge effect under it),
    /// `safeAreaInset` on the `.bar` background below.
    @ViewBuilder
    func bottomBar<Bar: View>(@ViewBuilder _ bar: () -> Bar) -> some View {
        let content = bar()
        if #available(iOS 26, *) {
            safeAreaBar(edge: .bottom, spacing: 0) { content }
        } else {
            safeAreaInset(edge: .bottom, spacing: 0) { content.background(.bar) }
        }
    }

    /// A bar pinned to the top: `safeAreaBar` on iOS 26, `safeAreaInset` below (no background,
    /// as the M4 chips sat directly under the search field).
    @ViewBuilder
    func topBar<Bar: View>(@ViewBuilder _ bar: () -> Bar) -> some View {
        let content = bar()
        if #available(iOS 26, *) {
            safeAreaBar(edge: .top, spacing: 0) { content }
        } else {
            safeAreaInset(edge: .top, spacing: 0) { content }
        }
    }
}

/// Groups glass controls so they blend and morph together: `GlassEffectContainer` on iOS 26,
/// a plain `Group` below.
struct GlassGroup<Content: View>: View {
    private let spacing: CGFloat?
    private let content: Content

    init(spacing: CGFloat? = nil, @ViewBuilder content: () -> Content) {
        self.spacing = spacing
        self.content = content()
    }

    var body: some View {
        if #available(iOS 26, *) {
            GlassEffectContainer(spacing: spacing) { content }
        } else {
            Group { content }
        }
    }
}
