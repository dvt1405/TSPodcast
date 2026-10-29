import SwiftUI

/// Temporary M0 screen listing trending podcasts; replaced by Home in M1.
struct TrendingDebugView: View {
    let model: TrendingDebugModel

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Trending (debug)")
        }
        .task { await model.loadIfNeeded() }
    }

    @ViewBuilder
    private var content: some View {
        switch model.state {
        case .loading:
            ProgressView("Đang tải…")
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        case .loaded(let rows):
            List {
                Section {
                    ForEach(rows) { row in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(row.title)
                                .font(.body)
                            if let author = row.author {
                                Text(author)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                } header: {
                    Text("\(rows.count) podcasts")
                }
            }
            .refreshable { await model.reload() }
        case .missingCredentials:
            failure(
                title: "Thiếu cấu hình PodcastIndex (Secrets.xcconfig)",
                message: "Chép iosApp/Config/Secrets.example.xcconfig thành Secrets.xcconfig, điền 4 key PODCASTINDEX_* rồi build lại."
            )
        case .failed(let message):
            failure(title: "Lỗi", message: message)
        }
    }

    private func failure(title: String, message: String) -> some View {
        ContentUnavailableView {
            Label(title, systemImage: "exclamationmark.triangle")
        } description: {
            Text(message)
        } actions: {
            Button("Tải lại") {
                Task { await model.reload() }
            }
            .buttonStyle(.borderedProminent)
        }
    }
}
