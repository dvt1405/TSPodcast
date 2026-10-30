#if DEBUG
import SwiftUI

/// `-uitest-playback-probe` (M3 §4.6): the player state as
/// `id=…;feedId=…;index=…;count=…;playing=…;buffering=…;positionMs=…;durationMs=…;live=…;fav=…;error=…`,
/// empty while nothing is loaded, so XCUITest can read what the engine does.
struct PlaybackProbe: View {
    let engine: AudioEngine

    static func label(_ state: PlaybackState) -> String {
        guard let item = state.current else { return "" }
        func flag(_ value: Bool) -> String { value ? "1" : "0" }
        return [
            "id=\(item.id)",
            "feedId=\(item.feedId)",
            "index=\(state.index)",
            "count=\(state.queue.count)",
            "playing=\(flag(state.isPlaying))",
            "buffering=\(flag(state.isBuffering))",
            "positionMs=\(state.positionMs)",
            "durationMs=\(state.durationMs)",
            "live=\(flag(state.isLive))",
            "fav=\(flag(state.isFavourite))",
            "error=\(flag(state.error != nil))",
        ].joined(separator: ";")
    }

    var body: some View {
        let label = Self.label(engine.state)
        Text(verbatim: label.isEmpty ? " " : label)
            .font(.system(size: 7, design: .monospaced))
            .foregroundStyle(.secondary)
            .lineLimit(1)
            .padding(.leading, 4)
            .allowsHitTesting(false)
            .accessibilityLabel(Text(verbatim: label))
            .accessibilityIdentifier("playback.probe")
    }
}
#endif
