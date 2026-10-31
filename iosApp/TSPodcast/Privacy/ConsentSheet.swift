import SwiftUI

/// First-launch privacy sheet (`consent.sheet`, M6 §3.6, Apple 5.1.1(iv)): the three groups,
/// "Cần thiết" always on, analytics and crash reports off until turned on, and two buttons of the
/// same style and size. Both close the sheet and go on into the app; swiping it away is disabled
/// so the choice is explicit. Every text is a placeholder waiting for the lawyer (`consent.*`).
struct ConsentSheet: View {
    let consent: ConsentController
    /// `TSPrivacyPolicyURL`; nil hides the link.
    let privacyURL: URL?

    @State private var analytics = false
    @State private var crash = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("consent.intro")
                        .font(.body)
                        .foregroundStyle(.secondary)

                    ConsentGroup(title: "consent.necessary.title", detail: "consent.necessary.detail") {
                        Toggle(isOn: .constant(true)) {
                            Text("consent.necessary.title")
                        }
                        .labelsHidden()
                        .disabled(true)
                        .accessibilityIdentifier("consent.necessary")
                    }
                    ConsentGroup(title: "consent.analytics.title", detail: "consent.analytics.detail") {
                        Toggle(isOn: $analytics) {
                            Text("consent.analytics.title")
                        }
                        .labelsHidden()
                        .accessibilityIdentifier("consent.analytics")
                    }
                    ConsentGroup(title: "consent.crash.title", detail: "consent.crash.detail") {
                        Toggle(isOn: $crash) {
                            Text("consent.crash.title")
                        }
                        .labelsHidden()
                        .accessibilityIdentifier("consent.crash")
                    }

                    if let privacyURL {
                        Link(destination: privacyURL) {
                            Text("settings.privacy")
                        }
                        .accessibilityIdentifier("consent.privacyPolicy")
                    }

                    Text("consent.footnote")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                .padding(16)
            }
            .safeAreaInset(edge: .bottom) {
                buttons
                    .padding(16)
                    .background(.bar)
            }
            .navigationTitle(Text("consent.title"))
            .navigationBarTitleDisplayMode(.inline)
        }
        .interactiveDismissDisabled()
        .accessibilityIdentifier("consent.sheet")
    }

    /// Same style, same width, side by side: neither choice is pushed over the other.
    private var buttons: some View {
        HStack(spacing: 12) {
            Button {
                Task { await consent.saveChoice(analytics: analytics, crash: crash) }
            } label: {
                Text(analytics || crash ? "consent.saveChoice" : "consent.necessaryOnly")
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
            .accessibilityIdentifier("consent.necessaryOnly")
            Button {
                Task { await consent.acceptAll() }
            } label: {
                Text("consent.acceptAll")
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
            .accessibilityIdentifier("consent.acceptAll")
        }
        .buttonStyle(.bordered)
        .tint(Color.brandPrimary)
        .font(.body.weight(.semibold))
    }
}

/// One group: title, explanation and its toggle on the trailing edge.
private struct ConsentGroup<Control: View>: View {
    let title: LocalizedStringKey
    let detail: LocalizedStringKey
    @ViewBuilder let control: Control

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.headline)
                Text(detail)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            control
        }
        .padding(12)
        .background(.quaternary.opacity(0.5), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        // `.contain`, not `.combine`: each toggle keeps its own identifier (`consent.analytics`, …).
        .accessibilityElement(children: .contain)
    }
}
