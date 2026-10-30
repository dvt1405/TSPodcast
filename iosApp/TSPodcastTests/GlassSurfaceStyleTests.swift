import XCTest
@testable import TSPodcast

/// M5-AC6: the material resolver behind `glassSurface` (plan §3.4).
final class GlassSurfaceStyleTests: XCTestCase {
    private func resolve(_ glass: Bool, _ reduceTransparency: Bool, _ contrast: Bool) -> GlassSurfaceStyle {
        GlassSurfaceStyle.resolve(
            isLiquidGlassAvailable: glass,
            reduceTransparency: reduceTransparency,
            increasedContrast: contrast
        )
    }

    func testLiquidGlassOnIOS26() {
        XCTAssertEqual(resolve(true, false, false), GlassSurfaceStyle(kind: .glass, showsBorder: false))
    }

    func testMaterialBeforeIOS26() {
        XCTAssertEqual(resolve(false, false, false), GlassSurfaceStyle(kind: .material, showsBorder: false))
    }

    func testReduceTransparencyIsOpaqueOnIOS26() {
        XCTAssertEqual(resolve(true, true, false), GlassSurfaceStyle(kind: .opaque, showsBorder: false))
    }

    func testReduceTransparencyAndIncreaseContrastBeforeIOS26() {
        XCTAssertEqual(resolve(false, true, true), GlassSurfaceStyle(kind: .opaque, showsBorder: true))
    }

    func testIncreaseContrastAddsBorderToGlass() {
        XCTAssertEqual(resolve(true, false, true), GlassSurfaceStyle(kind: .glass, showsBorder: true))
    }

    func testThisDeviceHasLiquidGlassOnIOS26() {
        if #available(iOS 26, *) {
            XCTAssertTrue(GlassSurfaceStyle.isLiquidGlassAvailable)
        } else {
            XCTAssertFalse(GlassSurfaceStyle.isLiquidGlassAvailable)
        }
    }
}
