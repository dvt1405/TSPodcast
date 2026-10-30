import SwiftUI

/// Settings sheet, opened from the gear of the Discover tab (`settings.open`).
struct SettingsView: View {
    @State var model: SettingsModel
    @Environment(\.dismiss) private var dismiss

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
                }

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

extension View {
    /// "Thông báo đang bị tắt trong Cài đặt của iPhone." with "Mở Cài đặt" and "Đóng".
    func notificationsDeniedAlert(_ notifications: NotificationsModel) -> some View {
        modifier(NotificationsDeniedAlert(notifications: notifications))
    }

    /// The one-time "Nhận thông báo khi có podcast mới?" prompt (after the first favourite).
    func notificationsPrompt(_ notifications: NotificationsModel) -> some View {
        modifier(NotificationsPrompt(notifications: notifications))
    }
}

private struct NotificationsDeniedAlert: ViewModifier {
    @Bindable var notifications: NotificationsModel

    func body(content: Content) -> some View {
        content.alert(Text("notifications.denied.message"), isPresented: $notifications.showDeniedAlert) {
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

    func body(content: Content) -> some View {
        content.alert(Text("notifications.prompt.title"), isPresented: $notifications.showPrompt) {
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
