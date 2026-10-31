import SwiftUI

/// Settings sheet, opened from the gear of the Discover tab (`settings.open`): notifications,
/// explicit content, hidden shows (M6), privacy choices (M6), privacy policy and version.
struct SettingsView: View {
    @State var model: SettingsModel
    @Environment(\.dismiss) private var dismiss
    @Environment(AppEnvironment.self) private var environment

    var body: some View {
        @Bindable var notifications = model.notifications
        NavigationStack {
            Form {
                Section {
                    Toggle("settings.notifications", isOn: Binding(
                        get: { model.notifications.isEnabled },
                        set: { enabled in Task { await model.setNotificationsEnabled(enabled) } }
                    ))
                    .accessibilityIdentifier("settings.notifications")

                    Toggle("settings.explicit", isOn: Binding(
                        get: { model.allowExplicit },
                        set: { allow in Task { await model.setAllowExplicit(allow) } }
                    ))
                    .accessibilityIdentifier("settings.explicit")

                    NavigationLink {
                        HiddenShowsView(model: environment.hiddenShows)
                    } label: {
                        Text("settings.hiddenShows")
                    }
                    .accessibilityIdentifier("settings.hiddenShows")
                }

                PrivacySection(consent: environment.consent)

                Section {
                    if let url = model.privacyURL {
                        Link(destination: url) {
                            Text("settings.privacy")
                        }
                        .accessibilityIdentifier("settings.privacy")
                    }
                    LabeledContent {
                        Text(verbatim: model.version)
                    } label: {
                        Text("settings.version")
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityIdentifier("settings.version")
                }
            }
            .navigationTitle(Text("settings.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        dismiss()
                    } label: {
                        Text("action.close")
                    }
                    .accessibilityIdentifier("settings.close")
                }
            }
            .notificationsDeniedAlert(notifications)
        }
        .accessibilityIdentifier("settings.root")
        .task { await model.load() }
    }
}

/// "Quyền riêng tư" (M6 §3.6): the two optional collections of the consent sheet, applied at
/// once, and when the choice was saved.
private struct PrivacySection: View {
    let consent: ConsentController

    var body: some View {
        Section {
            Toggle("consent.analytics.title", isOn: Binding(
                get: { consent.snapshot.analytics },
                set: { allowed in Task { await consent.setAnalytics(allowed) } }
            ))
            .accessibilityIdentifier("settings.analytics")

            Toggle("consent.crash.title", isOn: Binding(
                get: { consent.snapshot.crash },
                set: { allowed in Task { await consent.setCrashReports(allowed) } }
            ))
            .accessibilityIdentifier("settings.crash")
        } header: {
            Text("settings.privacySection")
        } footer: {
            if let decidedAt = consent.snapshot.decidedAt {
                Text("settings.consent.decidedAt \(decidedAt.formatted(date: .abbreviated, time: .shortened))")
                    .accessibilityIdentifier("settings.consent.decidedAt")
            }
        }
    }
}

extension View {
    /// "Thông báo đang bị tắt trong Cài đặt của iPhone." with "Mở Cài đặt" and "Đóng".
    /// `isActive: false` presents nothing (for a view that may be presenting something else).
    func notificationsDeniedAlert(_ notifications: NotificationsModel, isActive: Bool = true) -> some View {
        modifier(NotificationsDeniedAlert(notifications: notifications, isActive: isActive))
    }

    /// The one-time "Nhận thông báo khi có podcast mới?" prompt (after the first favourite).
    func notificationsPrompt(_ notifications: NotificationsModel, isActive: Bool = true) -> some View {
        modifier(NotificationsPrompt(notifications: notifications, isActive: isActive))
    }
}

private struct NotificationsDeniedAlert: ViewModifier {
    @Bindable var notifications: NotificationsModel
    let isActive: Bool

    func body(content: Content) -> some View {
        content.alert(Text("notifications.denied.message"), isPresented: isActive ? $notifications.showDeniedAlert : .constant(false)) {
            Button {
                notifications.openSystemSettings()
            } label: {
                Text("notifications.denied.openSettings")
            }
            .accessibilityIdentifier("notifications.denied.openSettings")
            Button(role: .cancel) {} label: {
                Text("action.close")
            }
        } message: {
            Text("notifications.denied.hint")
                .accessibilityIdentifier("notifications.denied")
        }
    }
}

private struct NotificationsPrompt: ViewModifier {
    @Bindable var notifications: NotificationsModel
    let isActive: Bool

    func body(content: Content) -> some View {
        content.alert(Text("notifications.prompt.title"), isPresented: isActive ? $notifications.showPrompt : .constant(false)) {
            Button {
                Task { await notifications.acceptPrompt() }
            } label: {
                Text("notifications.prompt.enable")
            }
            .accessibilityIdentifier("notifications.prompt.enable")
            Button(role: .cancel) {
                Task { await notifications.declinePrompt() }
            } label: {
                Text("notifications.prompt.later")
            }
            .accessibilityIdentifier("notifications.prompt.later")
        } message: {
            Text("notifications.prompt.message")
                .accessibilityIdentifier("notifications.prompt")
        }
    }
}
