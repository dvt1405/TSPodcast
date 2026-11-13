import StoreKit
import SwiftUI

/// "TSPodcast Premium" (contract §3, App Store 3.1.2): the benefit (no ads), the products of
/// `iap_products` in `order` with the store's price and period, the badge, a free trial only when
/// the Apple ID is eligible, the preselected `iap_default_product`, the buy button, "Khôi phục
/// giao dịch", the auto-renew disclosure, Terms of Use (Apple's EULA unless `TERMS_OF_USE_URL`)
/// and the privacy policy (`TSPrivacyPolicyURL`). Rows the store did not return are hidden; no
/// row at all shows the empty / error state with "Tải lại".
struct PaywallView: View {
    @State var model: PaywallModel
    @Environment(\.dismiss) private var dismiss

    private let termsURL = PaywallModel.termsURL(from: Bundle.main.infoDictionary ?? [:])
    private let privacyURL = SettingsModel.privacyURL(from: Bundle.main.infoDictionary ?? [:])

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                header
                if model.store.isPremium {
                    PremiumActiveCard()
                } else {
                    benefits
                    products
                }
                footer
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 24)
        }
        .bottomBar {
            if !model.store.isPremium, model.selected != nil {
                buyButton
            }
        }
        .navigationTitle(Text("paywall.title"))
        .navigationBarTitleDisplayMode(.inline)
        .accessibilityIdentifier("paywall.root")
        .task { await model.load() }
        .onChange(of: model.store.isPremium) { _, premium in
            if premium { dismiss() }
        }
        .alert(messageTitle, isPresented: Binding(
            get: { model.message != nil },
            set: { if !$0 { model.message = nil } }
        )) {
            Button("action.close", role: .cancel) { model.message = nil }
        }
    }

    private var header: some View {
        VStack(spacing: 10) {
            Image(systemName: "crown.fill")
                .font(.system(size: 44, weight: .semibold))
                .foregroundStyle(GradientButtonStyle.gradient)
                .accessibilityHidden(true)
            Text("paywall.title")
                .font(.title2.weight(.bold))
                .foregroundStyle(Color.textTitle)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            Text("paywall.subtitle")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 12)
    }

    private var benefits: some View {
        VStack(alignment: .leading, spacing: 12) {
            BenefitRow(icon: "rectangle.slash", text: "paywall.benefit.noBanners")
            BenefitRow(icon: "play.slash", text: "paywall.benefit.noFullScreen")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    @ViewBuilder
    private var products: some View {
        let options = model.options
        if !options.isEmpty {
            VStack(spacing: 12) {
                ForEach(options) { option in
                    Button {
                        model.selectedID = option.id
                    } label: {
                        OptionCard(option: option, isSelected: model.selected?.id == option.id)
                    }
                    .buttonStyle(.plain)
                    .disabled(model.isWorking)
                    .accessibilityAddTraits(model.selected?.id == option.id ? .isSelected : [])
                    .accessibilityIdentifier("paywall.option.\(option.id)")
                }
            }
        } else if model.store.productsState == .loading || model.store.productsState == .idle {
            ProgressView()
                .frame(maxWidth: .infinity, minHeight: 120)
                .accessibilityIdentifier("paywall.loading")
        } else {
            VStack(spacing: 12) {
                Image(systemName: "bag.badge.questionmark")
                    .font(.title2)
                    .foregroundStyle(.secondary)
                    .accessibilityHidden(true)
                Text("paywall.empty")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .accessibilityIdentifier("paywall.empty")
                Button("action.retry") {
                    Task { await model.retry() }
                }
                .glassButton()
                .tint(Color.brandPrimary)
                .accessibilityIdentifier("paywall.retry")
            }
            .frame(maxWidth: .infinity, minHeight: 160)
            .padding(16)
            .background(.quaternary.opacity(0.5), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
    }

    private var footer: some View {
        VStack(spacing: 14) {
            if !model.store.isPremium {
                Button {
                    Task { await model.restore() }
                } label: {
                    Text("paywall.restore")
                }
                .glassButton()
                .tint(Color.brandPrimary)
                .disabled(model.isWorking)
                .accessibilityIdentifier("paywall.restore")
            }
            if model.hasSubscription || model.store.isPremium {
                Text("paywall.disclosure")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .accessibilityIdentifier("paywall.disclosure")
            }
            HStack(spacing: 16) {
                Link(destination: termsURL) { Text("paywall.terms") }
                    .accessibilityIdentifier("paywall.terms")
                if let privacyURL {
                    Link(destination: privacyURL) { Text("settings.privacy") }
                        .accessibilityIdentifier("paywall.privacy")
                }
            }
            .font(.footnote)
            .tint(Color.brandPrimary)
        }
        .frame(maxWidth: .infinity)
    }

    private var buyButton: some View {
        Button {
            Task { await model.buy() }
        } label: {
            if model.isWorking {
                ProgressView().tint(.white)
            } else if model.selected?.trial != nil {
                Text("paywall.buy.trial")
            } else {
                Text("paywall.buy")
            }
        }
        .buttonStyle(GradientButtonStyle(cornerRadius: .infinity, fillsWidth: true))
        .disabled(model.isWorking)
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .accessibilityIdentifier("paywall.buy")
    }

    private var messageTitle: Text {
        switch model.message {
        case .pending: Text("paywall.pending")
        case .failed: Text("paywall.error")
        case .restored: Text("paywall.restore.done")
        case .nothingToRestore: Text("paywall.restore.none")
        case .restoreFailed: Text("paywall.restore.error")
        case nil: Text(verbatim: "")
        }
    }
}

private struct BenefitRow: View {
    let icon: String
    let text: LocalizedStringKey

    var body: some View {
        Label {
            Text(text)
                .font(.subheadline)
                .foregroundStyle(.primary)
        } icon: {
            Image(systemName: icon)
                .foregroundStyle(Color.brandPrimary)
        }
    }
}

/// A product: title, price + period (or "trọn đời"), the free trial, the "best value" badge.
private struct OptionCard: View {
    let option: PaywallOption
    let isSelected: Bool

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)
        HStack(alignment: .center, spacing: 12) {
            Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                .font(.title3)
                .foregroundStyle(isSelected ? Color.brandPrimary : Color.secondary)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(verbatim: option.title)
                        .font(.headline)
                        .foregroundStyle(.primary)
                    if option.isBestValue {
                        Text("paywall.badge.bestValue")
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.brandPrimary, in: Capsule())
                    }
                }
                if let trial = option.trial {
                    Text("paywall.trial \(PaywallModel.periodText(trial))")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(Color.brandPrimary)
                }
                if option.period == nil {
                    Text("paywall.lifetime")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 2) {
                Text(verbatim: option.price)
                    .font(.headline)
                    .foregroundStyle(.primary)
                if let period = option.period {
                    Text("paywall.per \(PaywallModel.periodText(period))")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(14)
        .background(Color(.secondarySystemBackground), in: shape)
        .overlay {
            shape.stroke(isSelected ? Color.brandPrimary : Color.stroke, lineWidth: isSelected ? 2 : 1)
        }
        .contentShape(shape)
        .accessibilityElement(children: .combine)
    }
}

private struct PremiumActiveCard: View {
    var body: some View {
        Label {
            Text("paywall.active")
                .font(.subheadline)
        } icon: {
            Image(systemName: "checkmark.seal.fill")
                .foregroundStyle(Color.brandPrimary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .accessibilityIdentifier("paywall.active")
    }
}
