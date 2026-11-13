import GoogleMobileAds
import SwiftUI
import UIKit
@preconcurrency import Shared

/// Banner placement (Android `AdBannerSlot`). Takes no space until the placement is allowed and
/// an ad actually loaded, so a no-fill, the kill switch or Premium never leave a gap.
struct AdBannerSlot: View {
    let placement: AdPlacement
    var slotKey = "0"

    @Environment(AppEnvironment.self) private var environment

    var body: some View {
        let ads = environment.ads
        if ads.allowsInline(placement) {
            let slot = ads.slot(placement, key: slotKey)
            Group {
                if let banner = slot.banner, slot.bannerHeight > 0 {
                    BannerContainer(banner: banner)
                        .frame(height: slot.bannerHeight)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 4)
                        .accessibilityIdentifier("ad.banner.\(placement.key)")
                } else {
                    Color.clear.frame(height: 0)
                }
            }
            .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width in
                ads.loadBanner(into: slot, width: width)
            }
        }
    }
}

/// Native placement (Android `AdNativeSlot`), same zero-size-when-empty contract.
struct AdNativeSlot: View {
    let placement: AdPlacement
    var slotKey = "0"

    @Environment(AppEnvironment.self) private var environment

    var body: some View {
        let ads = environment.ads
        if ads.allowsInline(placement) {
            let slot = ads.slot(placement, key: slotKey)
            Group {
                if let nativeAd = slot.nativeAd {
                    NativeAdContainer(nativeAd: nativeAd, template: ads.nativeTemplate(for: placement))
                        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .accessibilityIdentifier("ad.native.\(placement.key)")
                } else {
                    Color.clear.frame(height: 0)
                }
            }
            .task(id: slot.key) { ads.loadNative(into: slot) }
        }
    }
}

/// Hosts the kept `BannerView`, moving it out of a previous container (a list row that scrolled
/// away and back gets a new container, not a new ad request).
private struct BannerContainer: UIViewRepresentable {
    let banner: BannerView

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        return view
    }

    func updateUIView(_ view: UIView, context: Context) {
        guard banner.superview !== view else { return }
        banner.removeFromSuperview()
        banner.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(banner)
        NSLayoutConstraint.activate([
            banner.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            banner.topAnchor.constraint(equalTo: view.topAnchor),
        ])
    }

    static func dismantleUIView(_ view: UIView, coordinator: ()) {
        view.subviews.forEach { $0.removeFromSuperview() }
    }
}

private struct NativeAdContainer: UIViewRepresentable {
    let nativeAd: NativeAd
    let template: NativeTemplate

    func makeUIView(context: Context) -> NativeAdTemplateView {
        NativeAdTemplateView(medium: template == .medium)
    }

    func updateUIView(_ view: NativeAdTemplateView, context: Context) {
        view.bind(nativeAd)
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: NativeAdTemplateView, context: Context) -> CGSize? {
        let width = proposal.width ?? uiView.window?.windowScene?.screen.bounds.width ?? 375
        let size = uiView.systemLayoutSizeFitting(
            CGSize(width: width, height: UIView.layoutFittingCompressedSize.height),
            withHorizontalFittingPriority: .required,
            verticalFittingPriority: .fittingSizeLevel
        )
        return CGSize(width: width, height: ceil(size.height))
    }
}

/// Native templates built in code (Android `NativeAdTemplateView`), inside the SDK's
/// `NativeAdView` so clicks and impressions are attributed. Both carry a visible "Quảng cáo"
/// label (AdMob native policy); AdChoices is drawn by the SDK in a corner.
/// - Small: icon, headline, body/advertiser and the call to action in one row.
/// - Medium: the same header, a `MediaView` (image or muted video) and a full-width button.
final class NativeAdTemplateView: NativeAdView {
    private let icon = UIImageView()
    private let headline = UILabel()
    private let body = UILabel()
    private let badge = PaddedLabel()
    private let cta = UIButton(type: .system)
    private let media: MediaView?
    private weak var boundAd: NativeAd?

    init(medium: Bool) {
        media = medium ? MediaView() : nil
        super.init(frame: .zero)
        build()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    private func build() {
        let accent = UIColor(named: "Primary") ?? .systemBlue

        badge.text = String(localized: "ads.label")
        badge.font = .systemFont(ofSize: 10, weight: .bold)
        badge.textColor = .white
        badge.backgroundColor = accent
        badge.layer.cornerRadius = 3
        badge.clipsToBounds = true
        badge.setContentHuggingPriority(.required, for: .horizontal)
        badge.setContentCompressionResistancePriority(.required, for: .horizontal)

        headline.font = .preferredFont(forTextStyle: .subheadline).withWeight(.semibold)
        headline.adjustsFontForContentSizeCategory = true
        headline.textColor = .label
        headline.numberOfLines = 1

        body.font = .preferredFont(forTextStyle: .footnote)
        body.adjustsFontForContentSizeCategory = true
        body.textColor = .secondaryLabel
        body.numberOfLines = 2

        var buttonConfig = UIButton.Configuration.filled()
        buttonConfig.baseBackgroundColor = accent
        buttonConfig.baseForegroundColor = .white
        buttonConfig.cornerStyle = .capsule
        buttonConfig.contentInsets = NSDirectionalEdgeInsets(top: 8, leading: 14, bottom: 8, trailing: 14)
        cta.configuration = buttonConfig
        // The SDK handles the tap on the call-to-action view.
        cta.isUserInteractionEnabled = false
        cta.setContentHuggingPriority(.required, for: .horizontal)
        cta.setContentCompressionResistancePriority(.required, for: .horizontal)

        icon.contentMode = .scaleAspectFill
        icon.layer.cornerRadius = 8
        icon.clipsToBounds = true
        icon.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            icon.widthAnchor.constraint(equalToConstant: 44),
            icon.heightAnchor.constraint(equalToConstant: 44),
        ])

        let titleRow = UIStackView(arrangedSubviews: [badge, headline])
        titleRow.axis = .horizontal
        titleRow.spacing = 6
        titleRow.alignment = .center

        let texts = UIStackView(arrangedSubviews: [titleRow, body])
        texts.axis = .vertical
        texts.spacing = 2

        let header = UIStackView(arrangedSubviews: [icon, texts])
        header.axis = .horizontal
        header.spacing = 10
        header.alignment = .center
        if media == nil { header.addArrangedSubview(cta) }

        let root = UIStackView(arrangedSubviews: [header])
        root.axis = .vertical
        root.spacing = 10
        if let media {
            media.contentMode = .scaleAspectFill
            media.clipsToBounds = true
            media.layer.cornerRadius = 8
            media.translatesAutoresizingMaskIntoConstraints = false
            media.heightAnchor.constraint(equalToConstant: 180).isActive = true
            root.addArrangedSubview(media)
            root.addArrangedSubview(cta)
            mediaView = media
        }
        root.isLayoutMarginsRelativeArrangement = true
        root.directionalLayoutMargins = NSDirectionalEdgeInsets(top: 12, leading: 12, bottom: 12, trailing: 12)
        root.translatesAutoresizingMaskIntoConstraints = false
        addSubview(root)
        NSLayoutConstraint.activate([
            root.leadingAnchor.constraint(equalTo: leadingAnchor),
            root.trailingAnchor.constraint(equalTo: trailingAnchor),
            root.topAnchor.constraint(equalTo: topAnchor),
            root.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])

        headlineView = headline
        bodyView = body
        iconView = icon
        callToActionView = cta
    }

    func bind(_ ad: NativeAd) {
        guard boundAd !== ad else { return }
        boundAd = ad
        headline.text = ad.headline
        let secondary = ad.body ?? ad.advertiser
        body.text = secondary
        body.isHidden = secondary?.isEmpty ?? true
        icon.image = ad.icon?.image
        icon.isHidden = icon.image == nil
        cta.configuration?.title = ad.callToAction
        cta.isHidden = ad.callToAction?.isEmpty ?? true
        media?.mediaContent = ad.mediaContent
        // Last: the SDK reads the asset views when the ad is assigned.
        nativeAd = ad
    }
}

/// The "Quảng cáo" badge: a label with a little padding.
private final class PaddedLabel: UILabel {
    private let insets = UIEdgeInsets(top: 1, left: 4, bottom: 1, right: 4)

    override func drawText(in rect: CGRect) {
        super.drawText(in: rect.inset(by: insets))
    }

    override var intrinsicContentSize: CGSize {
        let size = super.intrinsicContentSize
        return CGSize(width: size.width + insets.left + insets.right, height: size.height + insets.top + insets.bottom)
    }
}

private extension UIFont {
    func withWeight(_ weight: UIFont.Weight) -> UIFont {
        let descriptor = fontDescriptor.addingAttributes([.traits: [UIFontDescriptor.TraitKey.weight: weight]])
        return UIFont(descriptor: descriptor, size: 0)
    }
}
