import SwiftUI
@preconcurrency import Shared

/// Playback speed sheet (M6 §3.2, N4): the big "1.5×", a 0.5–3.0 slider in 0.1 steps with −/+,
/// the five chips and "Áp dụng cho mọi show". Every change applies at once through
/// `AudioEngine.setSpeed`, which remembers it for the show (or for all) and logs `speed_change`;
/// a slider drag applies when it ends, so one drag is one change.
struct SpeedSheet: View {
    let engine: AudioEngine

    @Environment(\.locale) private var locale
    /// Slider value while the thumb is dragged.
    @State private var dragging: Double?

    var body: some View {
        let current = engine.state.speedTenths
        let shown = dragging.map(SpeedSteps.tenths(sliderValue:)) ?? current
        ScrollView {
            VStack(spacing: 20) {
                Text("player.speed")
                    .font(.headline)
                    .accessibilityAddTraits(.isHeader)
                Text(verbatim: SpeedSteps.label(shown))
                    .font(.largeTitle.weight(.bold).monospacedDigit())
                    .accessibilityLabel(Text(verbatim: SpeedSteps.spokenLabel(shown, locale: locale)))

                HStack(spacing: 12) {
                    stepButton(systemImage: "minus", label: "speed.minus", identifier: "speed.minus") {
                        set(SpeedSteps.decreased(current))
                    }
                    .disabled(!SpeedSteps.canDecrease(current))
                    Slider(
                        value: Binding(
                            get: { dragging ?? SpeedSteps.sliderValue(tenths: current) },
                            set: { value in
                                if dragging != nil {
                                    dragging = value
                                } else {
                                    // VoiceOver adjust (no drag): apply right away.
                                    set(SpeedSteps.tenths(sliderValue: value))
                                }
                            }
                        ),
                        in: SpeedSteps.sliderValue(tenths: SpeedSteps.minTenths)...SpeedSteps.sliderValue(tenths: SpeedSteps.maxTenths),
                        step: 0.1,
                        onEditingChanged: { editing in
                            if editing {
                                dragging = SpeedSteps.sliderValue(tenths: current)
                            } else {
                                if let dragging { set(SpeedSteps.tenths(sliderValue: dragging)) }
                                dragging = nil
                            }
                        }
                    )
                    .tint(Color.brandSecondary)
                    .accessibilityLabel(Text("player.speed"))
                    .accessibilityValue(Text(verbatim: SpeedSteps.spokenLabel(shown, locale: locale)))
                    .accessibilityIdentifier("speed.slider")
                    stepButton(systemImage: "plus", label: "speed.plus", identifier: "speed.plus") {
                        set(SpeedSteps.increased(current))
                    }
                    .disabled(!SpeedSteps.canIncrease(current))
                }

                GlassGroup(spacing: 8) {
                    HStack(spacing: 8) {
                        ForEach(SpeedSteps.chips, id: \.self) { chip in
                            chipButton(chip, isSelected: chip == shown)
                        }
                    }
                }

                VStack(alignment: .leading, spacing: 4) {
                    Toggle(
                        "speed.applyAll",
                        isOn: Binding(
                            get: { engine.appliesSpeedToAll },
                            set: { engine.setSpeedAppliesToAll($0) }
                        )
                    )
                    .tint(Color.brandPrimary)
                    .accessibilityIdentifier("speed.applyAll")
                    Text("speed.applyAll.footnote")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 24)
        }
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("speed.sheet")
    }

    private func set(_ tenths: Int) {
        engine.setSpeed(tenths: tenths, screen: AudioEngine.analyticsScreen)
    }

    private func stepButton(systemImage: String, label: LocalizedStringKey, identifier: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.title3.weight(.semibold))
                .frame(width: 44, height: 44)
                .contentShape(Circle())
        }
        .glassCircleButton()
        .accessibilityLabel(Text(label))
        .accessibilityIdentifier(identifier)
    }

    @ViewBuilder
    private func chipButton(_ tenths: Int, isSelected: Bool) -> some View {
        let button = Button {
            set(tenths)
        } label: {
            Text(verbatim: SpeedSteps.label(tenths))
                .font(.subheadline.weight(.semibold).monospacedDigit())
                .lineLimit(1)
                .frame(maxWidth: .infinity, minHeight: 44)
        }
        Group {
            if isSelected {
                button.glassProminentButton(tint: .brandPrimary)
            } else {
                button.glassButton()
            }
        }
        .accessibilityLabel(Text(verbatim: SpeedSteps.spokenLabel(tenths, locale: locale)))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .accessibilityIdentifier("speed.chip.\(tenths)")
    }
}
