import SwiftUI
@preconcurrency import Shared

/// The sleep timer sheet (M6 §3.1): 5 / 10 / 15 / 30 / 45 / 60 minutes, "Hết tập" and "Tắt hẹn
/// giờ", the current one checked, with the time left on top while a timer runs. Picking a row
/// sets the timer (`sleep_timer_set`, origin `player`) and closes the sheet.
struct SleepTimerSheet: View {
    let engine: AudioEngine

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let status = engine.state.sleepTimer
        NavigationStack {
            List {
                if status.isActive {
                    Section {
                        TimelineView(.periodic(from: .now, by: 1)) { context in
                            statusText(status, at: context.date)
                                .font(.body.monospacedDigit())
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                Section {
                    ForEach(SleepTimerSetting.sheetOptions, id: \.self) { option in
                        let isSelected = option == status.setting
                        Button {
                            engine.setSleepTimer(option, origin: AnalyticsEvents.shared.ORIGIN_PLAYER)
                            dismiss()
                        } label: {
                            HStack {
                                title(option)
                                    .foregroundStyle(.primary)
                                Spacer()
                                if isSelected {
                                    Image(systemName: "checkmark")
                                        .foregroundStyle(Color.brandPrimary)
                                        .accessibilityHidden(true)
                                }
                            }
                            .frame(minHeight: 44)
                            .contentShape(Rectangle())
                        }
                        .accessibilityAddTraits(isSelected ? .isSelected : [])
                        .accessibilityIdentifier("sleepTimer.option.\(option.identifierSuffix)")
                    }
                }
            }
            .navigationTitle(Text("player.sleepTimer"))
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("sleepTimer.sheet")
    }

    private func title(_ option: SleepTimerSetting) -> Text {
        switch option {
        case let .minutes(minutes):
            Text("sleepTimer.minutes \(minutes)")
        case .endOfEpisode:
            Text("sleepTimer.option.endOfEpisode")
        case .off:
            Text("sleepTimer.option.off")
        }
    }

    private func statusText(_ status: SleepTimerStatus, at now: Date) -> Text {
        switch status.setting {
        case .minutes:
            let clock = SleepTimerStatus.clockText(remainingMs: status.remainingMs(at: now) ?? 0)
            return Text(verbatim: String(format: String(localized: "player.sleepTimer.remaining"), clock))
        case .endOfEpisode:
            return Text("sleepTimer.status.endOfEpisode")
        case .off:
            return Text(verbatim: "")
        }
    }
}
