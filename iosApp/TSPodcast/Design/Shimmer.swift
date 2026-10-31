import SwiftUI

/// Loading placeholder: `.redacted(reason: .placeholder)` plus a moving highlight, the iOS
/// counterpart of Android's shimmer rows. The highlight stops when Reduce Motion is on.
struct Shimmer: ViewModifier {
    var isActive: Bool = true

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var phase: CGFloat = -1

    func body(content: Content) -> some View {
        if isActive {
            content
                .redacted(reason: .placeholder)
                .overlay {
                    if !reduceMotion {
                        highlight
                            .mask(content.redacted(reason: .placeholder))
                            .allowsHitTesting(false)
                    }
                }
                .onAppear {
                    guard !reduceMotion else { return }
                    withAnimation(.linear(duration: 1.2).repeatForever(autoreverses: false)) {
                        phase = 1
                    }
                }
                .accessibilityHidden(true)
        } else {
            content
        }
    }

    private var highlight: some View {
        GeometryReader { proxy in
            let width = proxy.size.width
            LinearGradient(
                colors: [.clear, .white.opacity(0.45), .clear],
                startPoint: .leading,
                endPoint: .trailing
            )
            .frame(width: width * 0.6)
            .offset(x: phase * width * 1.3)
            .frame(width: width, alignment: .center)
        }
    }
}

extension View {
    /// Redacts the view and animates a shimmer over it while `isActive`.
    func shimmer(_ isActive: Bool = true) -> some View {
        modifier(Shimmer(isActive: isActive))
    }
}
