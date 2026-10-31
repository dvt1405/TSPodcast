import UIKit
import XCTest
@testable import TSPodcast

/// M1-AC12: every id of Android's `_mapIcon` has an icon in the asset catalog.
final class CategoryIconTests: XCTestCase {
    func testMapHasTheAndroidIds() {
        XCTAssertEqual(CategoryIcon.assetNames.count, 103)
        XCTAssertEqual(CategoryIcon.assetName(for: 1), "ic_art")
        XCTAssertEqual(CategoryIcon.assetName(for: 112), "ic_currency_100")
        XCTAssertNil(CategoryIcon.assetNames[11], "Android has no icon for id 11")
    }

    func testEveryMappedIdHasAnImage() {
        let missing = CategoryIcon.assetNames
            .filter { UIImage(named: $0.value) == nil }
            .map(\.key)
            .sorted()
        XCTAssertEqual(missing, [])
    }

    func testUnknownIdUsesTheDefaultIcon() {
        XCTAssertEqual(CategoryIcon.assetName(for: 9999), CategoryIcon.defaultAssetName)
        XCTAssertEqual(CategoryIcon.assetName(for: 11), "ic_anonymous")
        XCTAssertNotNil(UIImage(named: CategoryIcon.defaultAssetName))
    }

    func testConvertedVectorIconsRenderWithContent() throws {
        // The 10 Android vector drawables used by the map were converted to SVG.
        for name in ["ic_anonymous", "ic_comedy", "ic_design", "ic_food", "ic_idea",
                     "ic_linkedin", "ic_my_space", "ic_social", "ic_stories", "ic_visual"] {
            let image = try XCTUnwrap(UIImage(named: name), name)
            XCTAssertGreaterThan(image.size.width, 0, name)
            let rendered = UIGraphicsImageRenderer(size: CGSize(width: 48, height: 48)).image { _ in
                image.draw(in: CGRect(x: 0, y: 0, width: 48, height: 48))
            }
            XCTAssertTrue(Self.hasOpaquePixels(rendered), "\(name) renders empty")
        }
    }

    private static func hasOpaquePixels(_ image: UIImage) -> Bool {
        guard let cgImage = image.cgImage,
              let data = cgImage.dataProvider?.data,
              let bytes = CFDataGetBytePtr(data) else { return false }
        let count = CFDataGetLength(data)
        let alphaOffset: Int
        switch cgImage.alphaInfo {
        case .premultipliedFirst, .first, .noneSkipFirst: alphaOffset = 0
        default: alphaOffset = 3
        }
        var opaque = 0
        for index in stride(from: alphaOffset, to: count, by: 4) where bytes[index] > 0 {
            opaque += 1
        }
        return opaque > 100
    }
}
