import SwiftUI

/// Pushes `NewsHubView` with a model built by `AppEnvironment`.
struct NewsHubDestination: View {
    let entry: NewsHubEntry
    let initial: NewsHubContent?

    @Environment(AppEnvironment.self) private var environment

    var body: some View {
        NewsHubView(model: environment.makeNewsHubModel(entry: entry, initial: initial))
    }
}

/// "Thời sự & Bản tin" (`newsHub.root`, M6 §3.4), pushed on the Discover stack: the morning
/// brief, "Mới nhất" (30, a tap plays that list without auto-advance), "Nguồn" (each opens the
/// ordinary Podcast detail), "Truyện đêm khuya" (plays with a 30-minute sleep timer and a toast
/// to change it), then the footer with "Báo cáo / yêu cầu gỡ".
///
/// Every text of ours comes from a `news.*` key; feed and episode titles are the publishers'
/// own words and are shown as they are.
struct NewsHubView: View {
    @State private var model: NewsHubModel
    @State private var isSleepTimerSheetPresented = false
    /// Address shown when no app could open the report mail.
    @State private var reportFallbackEmail: String?

    @Environment(AppEnvironment.self) private var environment
    @Environment(\.openURL) private var openURL

    init(model: NewsHubModel) {
        _model = State(initialValue: model)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                switch model.state {
                case .loading:
                    ProgressView()
                        .frame(maxWidth: .infinity, minHeight: 240)
                        .accessibilityIdentifier("newsHub.loading")
                case .failed:
                    message(text: "news.error", systemImage: "wifi.exclamationmark", showsRetry: true)
                        .accessibilityIdentifier("newsHub.error")
                case .unavailable:
                    message(text: "news.unavailable", systemImage: "newspaper", showsRetry: false)
                        .accessibilityIdentifier("newsHub.unavailable")
                case .loaded(let content):
                    sections(content)
                }
                footer
            }
            .padding(.bottom, 24)
        }
        .accessibilityIdentifier("newsHub.root")
        .navigationTitle(Text("news.title"))
        .toolbarTitleDisplayMode(.inline)
        .refreshable { await model.retry() }
        .task { await model.loadIfNeeded() }
        .safeAreaInset(edge: .bottom) {
            if let minutes = model.toastMinutes {
                SleepTimerToast(minutes: minutes) {
                    model.dismissToast()
                    isSleepTimerSheetPresented = true
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 8)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.spring(duration: 0.3), value: model.toastMinutes)
        .sheet(isPresented: $isSleepTimerSheetPresented) {
            SleepTimerSheet(engine: environment.audioEngine)
        }
        .contentReportFallbackAlert(email: $reportFallbackEmail)
    }

    // MARK: - Sections

    @ViewBuilder
    private func sections(_ content: NewsHubContent) -> some View {
        if !content.brief.isEmpty {
            BriefCard(content: content, identifier: "newsHub.brief") {
                model.playBrief()
            }
            .padding(.horizontal, 16)
            .padding(.top, 16)
        }

        SectionTitle(title: "news.latest")
        LazyVStack(alignment: .leading, spacing: 16) {
            ForEach(Array(content.latest.enumerated()), id: \.offset) { index, item in
                Button {
                    model.playLatest(at: index)
                } label: {
                    NewsEpisodeRow(item: item)
                }
                .buttonStyle(PressableStyle())
                .hideShowContextMenu(feedId: item.feedId, title: item.feedTitle, imageURL: item.imageURL, screen: NewsHubModel.analyticsScreen)
                .accessibilityIdentifier("newsHub.latest.item.\(index)")
            }
        }
        .padding(.horizontal, 16)

        if !content.sources.isEmpty {
            SectionTitle(title: "news.sources")
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 100), spacing: 12, alignment: .top)], spacing: 16) {
                ForEach(Array(content.sources.enumerated()), id: \.offset) { index, source in
                    NavigationLink(value: DiscoverRoute.podcast(source.route)) {
                        SourceTile(source: source)
                    }
                    .buttonStyle(PressableStyle())
                    .hideShowContextMenu(feedId: source.id, title: source.title, imageURL: source.imageURL, screen: NewsHubModel.analyticsScreen)
                    .accessibilityLabel(Text(verbatim: source.title))
                    .accessibilityIdentifier("newsHub.source.\(index)")
                }
            }
            .padding(.horizontal, 16)
        }

        if !content.night.isEmpty {
            SectionTitle(title: "news.night", systemImage: "moon.stars")
            Text("news.night.subtitle \(content.nightSleepTimerMinutes)")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .padding(.horizontal, 16)
                .padding(.bottom, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
            LazyVStack(alignment: .leading, spacing: 16) {
                ForEach(Array(content.night.enumerated()), id: \.offset) { index, item in
                    Button {
                        model.playNight(at: index)
                    } label: {
                        NewsEpisodeRow(item: item, systemImage: "moon.zzz")
                    }
                    .buttonStyle(PressableStyle())
                    .hideShowContextMenu(feedId: item.feedId, title: item.feedTitle, imageURL: item.imageURL, screen: NewsHubModel.analyticsScreen)
                    .accessibilityHint(Text("news.night.subtitle \(content.nightSleepTimerMinutes)"))
                    .accessibilityIdentifier("newsHub.night.item.\(index)")
                }
            }
            .padding(.horizontal, 16)
        }
    }

    /// The footer, word for word (§3.4), and the report link when an address is configured.
    private var footer: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("news.footer")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .accessibilityIdentifier("newsHub.footer")
            if environment.contentReporter.isAvailable {
                Button {
                    report()
                } label: {
                    Text("news.report")
                        .font(.footnote.weight(.semibold))
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                }
                .accessibilityIdentifier("newsHub.report")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.top, 28)
    }

    private func message(text: LocalizedStringKey, systemImage: String, showsRetry: Bool) -> some View {
        VStack(spacing: 12) {
            Image(systemName: systemImage)
                .font(.title2)
                .foregroundStyle(.secondary)
                .accessibilityHidden(true)
            Text(text)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            if showsRetry {
                Button("action.retry") {
                    Task { await model.retry() }
                }
                .glassButton()
                .tint(Color.brandPrimary)
                .accessibilityIdentifier("newsHub.retry")
            }
        }
        .frame(maxWidth: .infinity, minHeight: 240)
        .padding(16)
    }

    private func report() {
        let openURL = openURL
        environment.contentReporter.report(
            kind: .hub,
            feedId: nil,
            episodeId: nil,
            screen: NewsHubModel.analyticsScreen,
            open: { url, completion in openURL(url, completion: completion) },
            failed: { email in reportFallbackEmail = email }
        )
    }
}

// MARK: - Building blocks

private struct SectionTitle: View {
    let title: LocalizedStringKey
    var systemImage: String?

    var body: some View {
        HStack(spacing: 8) {
            if let systemImage {
                Image(systemName: systemImage)
                    .foregroundStyle(Color.brandPrimary)
                    .accessibilityHidden(true)
            }
            Text(title)
        }
        .font(.title3.weight(.bold))
        .foregroundStyle(Color.textTitle)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.top, 24)
        .padding(.bottom, 12)
    }
}

/// A source of "Nguồn": the feed cover and its PodcastIndex title.
private struct SourceTile: View {
    let source: NewsSourceCard

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ArtworkImage(url: source.imageURL, cornerRadius: 12)
                .aspectRatio(1, contentMode: .fit)
            Text(verbatim: source.title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.primary)
                .lineLimit(2, reservesSpace: true)
                .multilineTextAlignment(.leading)
        }
        .contentShape(Rectangle())
    }
}

/// "Đã hẹn giờ tắt 30 phút · Đổi" after a night shelf play; announced to VoiceOver when shown.
private struct SleepTimerToast: View {
    let minutes: Int
    let change: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "moon.zzz.fill")
                .foregroundStyle(Color.brandPrimary)
                .accessibilityHidden(true)
            Text("news.toast \(minutes)")
                .font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: change) {
                Text("news.toast.change")
                    .font(.subheadline.weight(.bold))
                    .frame(minWidth: 44, minHeight: 44)
                    .contentShape(Rectangle())
            }
            .accessibilityIdentifier("newsHub.toast.change")
        }
        .padding(.leading, 16)
        .padding(.trailing, 8)
        .glassSurface(in: Capsule())
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("newsHub.toast")
        .onAppear {
            AccessibilityNotification.Announcement(String(localized: "news.toast \(minutes)")).post()
        }
    }
}
