import SwiftUI

/// Icon of a PodcastIndex category, by id, like Android's `_mapIcon` in
/// `SelectFavouriteCategoryScreen`: 103 ids, unknown ids use `ic_anonymous`. The table lives in
/// `CategoryIconMap.swift`, generated together with the assets in `CategoryIcons/`.
enum CategoryIcon {
    static let defaultAssetName = "ic_anonymous"

    static func assetName(for id: Int) -> String {
        assetNames[id] ?? defaultAssetName
    }

    static func image(for id: Int) -> Image {
        Image(assetName(for: id))
    }
}
