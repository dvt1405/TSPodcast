import SwiftUI
import UIKit

extension View {
    /// The alert shown when no app could open the report mail: the address, with "Sao chép".
    /// `email` is the address to show; the alert sets it back to nil when dismissed.
    func contentReportFallbackAlert(email: Binding<String?>) -> some View {
        modifier(ContentReportFallbackAlert(email: email))
    }
}

private struct ContentReportFallbackAlert: ViewModifier {
    @Binding var email: String?

    func body(content: Content) -> some View {
        content.alert(
            Text("report.fallback.title"),
            isPresented: Binding(get: { email != nil }, set: { if !$0 { email = nil } }),
            presenting: email
        ) { address in
            Button {
                UIPasteboard.general.string = address
            } label: {
                Text("report.fallback.copy")
            }
            .accessibilityIdentifier("report.fallback.copy")
            Button(role: .cancel) {} label: {
                Text("action.close")
            }
        } message: { address in
            Text("report.fallback.message \(address)")
        }
    }
}
