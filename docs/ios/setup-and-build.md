# Setup và build

## 1. Máy Mac và công cụ

| Thành phần | Yêu cầu / khuyến nghị |
|---|---|
| Phần cứng | Mac Apple Silicon (M1 trở lên), **RAM ≥ 16 GB** (32 GB nếu chạy Android Studio, Xcode và Gradle daemon cùng lúc), SSD còn trống ≥ 100 GB (gồm Xcode, simulator và `~/.konan`) |
| macOS | Phiên bản mà Xcode 26.x yêu cầu (macOS Sequoia 15.x bản mới hoặc macOS Tahoe 26). Xem trang Xcode system requirements |
| Xcode | **26.x trở lên**. Từ 28/4/2026, App Store Connect chỉ nhận bản build bằng Xcode 26 với iOS 26 SDK (https://developer.apple.com/news/upcoming-requirements/). Apple thường nâng mốc này vào khoảng tháng 4 hằng năm, nên cần kiểm tra lại trang này trước mỗi đợt release |
| JDK | 17 (Temurin) cho Gradle/AGP 8.13. Đặt `JAVA_HOME` trỏ tới JDK 17 |
| IDE Kotlin | Android Studio (bản mới nhất) cùng plugin **Kotlin Multiplatform** của JetBrains, hoặc IntelliJ IDEA |
| Homebrew | `brew install xcodes rbenv` (quản lý Xcode, Ruby) |
| Ruby + Bundler | Để chạy fastlane (`Gemfile` trong `iosApp/`) |
| Tuỳ chọn | Proxyman/Charles (debug stream), CarPlay Simulator (Additional Tools for Xcode), SF Symbols |

```bash
xcodes install 26.x && sudo xcode-select -s /Applications/Xcode-26.x.app
xcodebuild -runFirstLaunch
xcrun simctl list runtimes            # đảm bảo đã có iOS 26 runtime
java -version                         # 17
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64   # kiểm tra toolchain K/N
```

## 2. Biến `sharedKmp` thành module KMP

`settings.gradle.kts`:

```kotlin
include(":shared")   // đổi tên thư mục sharedKmp → shared (hoặc giữ ":sharedKmp")
```

`gradle/libs.versions.toml` (thêm, đối chiếu version ở architecture.md §7):

```toml
[versions]
kotlin = "2.3.21"           # khớp SKIE
ksp = "2.3.21-x.y.z"
ktor = "<3.x>"
serialization = "<1.9.x>"
koin = "<4.x>"
datastore = "<1.1.x>"
sqlite = "2.6.2"
skie = "<0.10.12+>"
[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
android-kmp-library = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
skie = { id = "co.touchlab.skie", version.ref = "skie" }
```

`shared/build.gradle.kts` (rút gọn):

```kotlin
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serilization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.skie)
}

kotlin {
    androidLibrary {                       // tên block DSL theo AGP 8.x; có thể đổi ở AGP mới
        namespace = "tss.t.shared"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    val xcf = XCFramework("Shared")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true                // static: không phải embed, dSYM gộp vào app
            xcf.add(this)
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.koin.core)
            implementation(libs.okio)
            implementation(libs.kermit)
            implementation(libs.androidx.lifecycle.viewmodel)   // KMP ViewModel
        }
        androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
        iosMain.dependencies { implementation(libs.ktor.client.darwin) }
        commonTest.dependencies { implementation(kotlin("test")); implementation(libs.ktor.client.mock) }
    }
}

dependencies {
    listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64").forEach {
        add(it, libs.androidx.room.room.compiler)
    }
}
room { schemaDirectory("$projectDir/schemas") }   // copy schemas v5, v6 từ core/schemas
```

## 3. Tạo project Xcode

1. Xcode → New Project → iOS App.
   - Product Name `TSPodcast`; Organization Identifier `tss.t`, nên **Bundle ID = `tss.t.podcast`** (giống `applicationId` của Android để dễ quản lý Firebase và App Store).
   - Interface SwiftUI, ngôn ngữ Swift, có Testing.
   - Đặt project ở `iosApp/`.
2. Deployment target **iOS 17.0** (lý do ở architecture.md §7). Thiết bị: iPhone. iPad: có thể tắt ở v1 để khỏi phải làm screenshot 13" và layout iPad, nhưng app vẫn chạy chế độ tương thích trên iPad. Bật iPad nếu muốn tăng phạm vi tiếp cận.
3. Signing & Capabilities:
   - **Background Modes → Audio, AirPlay, and Picture in Picture**;
   - **Push Notifications**;
   - (sau) **CarPlay** khi đã được cấp entitlement.
4. Swift Package Manager:
   - `firebase-ios-sdk`: chọn `FirebaseAnalytics`, `FirebaseCrashlytics`, `FirebaseRemoteConfig`, `FirebaseMessaging`.
   - Image loader (`Nuke` hoặc `Kingfisher`), hoặc `AsyncImage` kèm `URLCache`.
   - (nếu có ads) `GoogleMobileAds` và `GoogleUserMessagingPlatform`.

### 3.1 Tích hợp framework Kotlin: chọn **một** trong ba cách

| Cách | Khi nào | Thiết lập |
|---|---|---|
| **A. Direct integration** (khuyến nghị cho monorepo) | Một repo, iOS dev có JDK | Thêm Build Phase "Compile Kotlin" **trước** "Compile Sources": `cd "$SRCROOT/.." && ./gradlew :shared:embedAndSignAppleFrameworkForXcode`. Build Settings: `FRAMEWORK_SEARCH_PATHS = $(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`, `OTHER_LDFLAGS = -framework Shared`, `ENABLE_USER_SCRIPT_SANDBOXING = NO` |
| **B. XCFramework thủ công** | Muốn tách bước build | `./gradlew :shared:assembleSharedReleaseXCFramework` → `shared/build/XCFrameworks/release/Shared.xcframework`, kéo vào Xcode và chọn "Do Not Embed" (static) |
| **C. Swift Package (remote)** | iOS dev không cài JDK, hoặc tách repo | Dùng [KMMBridge](https://touchlab.co/kmmbridge) để publish file zip XCFramework (GitHub Releases/Packages) cùng `Package.swift` có `binaryTarget(url:checksum:)`. iOS app thêm package qua SPM |

Gradle tasks hữu ích:

```bash
./gradlew :shared:allTests                                 # test common + iOS simulator
./gradlew :shared:iosSimulatorArm64Test
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
./gradlew :shared:assembleSharedXCFramework                # debug + release
./gradlew :shared:assembleSharedReleaseXCFramework
```

Lần build Kotlin/Native đầu tiên sẽ tải toolchain về `~/.konan` (vài GB). Trên CI cần cache thư mục này.

### 3.2 Khởi tạo trong Swift

```swift
@main
struct TSPodcastApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    var body: some Scene { WindowGroup { RootView() } }
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ app: UIApplication,
                     didFinishLaunchingWithOptions opts: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        FirebaseApp.configure()
        CrashKiOSBridge.install()                         // §6.2
        KoinIosKt.doInitKoin(
            credentials: BundleCredentialsProvider(),
            crash: FirebaseCrashReporter(),
            config: FirebaseRemoteConfigAdapter(),
            analytics: FirebaseAnalyticsTracker(),
            player: AudioEngine.shared)
        Task { try? await SharedDI.shared.pruneApiCache().invoke() }
        return true
    }
}
```

### 3.3 Info.plist quan trọng

```xml
<key>CFBundleDevelopmentRegion</key><string>vi</string>
<key>ITSAppUsesNonExemptEncryption</key><false/>        <!-- chỉ dùng HTTPS của hệ thống -->
<key>UIBackgroundModes</key><array><string>audio</string></array>
<key>NSAppTransportSecurity</key>
<dict>
  <key>NSAllowsArbitraryLoadsForMedia</key><true/>      <!-- enclosure/stream http:// -->
  <!-- Chỉ thêm NSExceptionDomains cho domain cụ thể nếu bắt buộc (ví dụ scrape radio http) -->
</dict>
<key>PODCASTINDEX_API_KEY</key><string>$(PODCASTINDEX_API_KEY)</string>   <!-- từ Secrets.xcconfig -->
<!-- ... PODCASTINDEX_API_SECRET, PODCASTINDEX_BASE_URL, PODCASTINDEX_USER_AGENT -->
```

**ATS và ảnh `http://`:** Android đang bật `usesCleartextTraffic=true`. Trên iOS, `NSAllowsArbitraryLoadsForMedia` chỉ áp dụng cho AVFoundation. Với ảnh cover `http://`, xử lý theo thứ tự:

1. thử tự nâng lên `https://` (đa số CDN hỗ trợ);
2. nếu lỗi thì hiện placeholder;
3. nếu tỷ lệ lỗi cao, dựng một **image proxy** HTTPS của riêng mình (ví dụ Cloudflare Worker có resize). Proxy này còn giúp không lộ IP người dùng cho publisher.

**Không** bật `NSAllowsArbitraryLoads` toàn cục; nếu bật thì phải ghi lý do trong review notes.

**Universal Link (M4 T5):** Info.plist có `TSAppLinkHost = $(APP_LINK_HOST)`; build setting `APP_LINK_HOST` ở `iosApp/Config/Shared.xcconfig`, mặc định rỗng nên app không nhận link nào. Khi owner có domain: điền host (không có `https://`), thêm entitlement `com.apple.developer.associated-domains` = `applinks:<host>` (cần capability Associated Domains trên App ID) và phục vụ `https://<host>/.well-known/apple-app-site-association` (handoff). Route: `/podcast/<feedId>-<slug>` → Podcast detail ở tab Khám phá; `/episode/<id>-<slug>` → phát + Detail; có thể có tiền tố `/en`.

**Privacy manifest (M4 T5):** `iosApp/TSPodcast/PrivacyInfo.xcprivacy` (nằm trong Resources của app). Sau mỗi bản Release, kiểm required-reason API trên binary thật:

```bash
iosApp/scripts/check-required-reason-apis.sh <DerivedData>/Build/Products/Release-iphonesimulator/TSPodcast.app
# in bảng bundle → nhóm → symbol → reason; "REQUIRED REASON CHECK PASS", exit 0
```

Script fail khi binary dùng một nhóm (FileTimestamp, SystemBootTime, DiskSpace, ActiveKeyboards, UserDefaults) mà manifest không khai, khi manifest khai nhóm thừa, hoặc khi reason không thuộc danh sách của Apple. Đổi dependency (Firebase, Kotlin, Room…) thì chạy lại và sửa manifest.

## 4. Firebase iOS

1. Firebase Console → (project **release** hiện có của Android) → Add app → iOS, bundle `tss.t.podcast`. Làm tương tự với project **debug** nếu muốn tách môi trường như Android (Android đang dùng 2 project Firebase riêng cho debug và release).
2. Tải `GoogleService-Info.plist`:
   - **không commit** nếu repo public;
   - lưu thành `Config/Firebase/Debug/GoogleService-Info.plist` và `.../Release/...`;
   - thêm Build Phase copy file đúng theo `$(CONFIGURATION)` vào `$(BUILT_PRODUCTS_DIR)/$(PRODUCT_NAME).app/`.
   - **Đã làm ở M4 T4:** hai đường dẫn trên đã gitignore; build phase "Copy GoogleService-Info.plist" (`iosApp/scripts/copy-firebase-plist.sh`) chép file khi có, không có thì app chạy bằng các seam log/no-op (log `firebase=disabled reason=missing_plist`). Build setting `FIREBASE_PLIST_DIR` (mặc định `iosApp/Config/Firebase`) đổi thư mục nguồn; trỏ tới thư mục rỗng để build bản không Firebase dù máy có plist: `xcodebuild … FIREBASE_PLIST_DIR=/tmp/no-firebase`.
3. **Remote Config:** dùng chung key với Android (`ts_podcast_black_list`, `ads_enable`). Có thể tạo **condition** theo platform iOS để tắt `ads_enable` trên iOS.
4. **Cloud Messaging:**
   - tạo APNs Auth Key (`.p8`) ở Certificates, Identifiers & Profiles → Keys;
   - upload lên Firebase → Project Settings → Cloud Messaging → Apple app configuration.
   - Gửi notification payload có `alert` (iOS không hiện được notification từ data-only message như Android). Ảnh lớn: thêm **Notification Service Extension**.
5. **Analytics:**
   - nếu không có ads, **không link `AdSupport.framework`** để Analytics không đọc IDFA;
   - kiểm tra bằng `nm`/Xcode báo cáo link;
   - đặt `GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_PERSONALIZATION_SIGNALS = NO` trong Info.plist khi không quảng cáo.

## 5. Bản địa hoá

- `Localizable.xcstrings` (String Catalog): development language **Vietnamese**, thêm **English**. Port các chuỗi từ `app/.../values/strings.xml`, `featureOnboarding/.../strings.xml` và `sharedResources/.../strings.xml`.
- `InfoPlist.xcstrings`: `CFBundleDisplayName`, `NSUserTrackingUsageDescription` (nếu có ads).
- Chuỗi do shared sinh ra (thông báo lỗi) nên trả về **mã lỗi**; Swift tự map sang chuỗi đã localize.

## 6. Crashlytics và dSYM

### 6.1 Swift / Obj-C

- Build Settings → **Debug Information Format = DWARF with dSYM File**, áp dụng cho cả Debug (để test) và Release.
- Build Phase (đặt cuối cùng) "Crashlytics":

```bash
"${BUILD_DIR%/Build/*}/SourcePackages/checkouts/firebase-ios-sdk/Crashlytics/run"
```

  Input Files:

```
${DWARF_DSYM_FOLDER_PATH}/${DWARF_DSYM_FILE_NAME}
${DWARF_DSYM_FOLDER_PATH}/${DWARF_DSYM_FILE_NAME}/Contents/Resources/DWARF/${PRODUCT_NAME}
${DWARF_DSYM_FOLDER_PATH}/${DWARF_DSYM_FILE_NAME}/Contents/Info.plist
$(TARGET_BUILD_DIR)/$(UNLOCALIZED_RESOURCES_FOLDER_PATH)/GoogleService-Info.plist
$(TARGET_BUILD_DIR)/$(EXECUTABLE_PATH)
```

- **Đã làm ở M4 T4:** build phase "Crashlytics dSYM" (`iosApp/scripts/crashlytics-upload-symbols.sh`) chỉ chạy cho **Release** và chỉ khi app có `GoogleService-Info.plist`; thiếu script hay plist thì bỏ qua, không fail build. Không khai Input Files (Xcode báo lỗi khi input không tồn tại, mà plist là tuỳ chọn).
- Kotlin/Native (§6.2): M4 dùng hook tự viết `installKotlinCrashHook` thay CrashKiOS (handoff, plan M4 §7 mục 7); `FirebaseCrashReporter.recordFatal` gửi `ExceptionModel` với địa chỉ stack.
- Trên CI/Xcode Cloud, upload dSYM của archive một cách tường minh:
  - `upload-symbols -gsp <plist> -p ios <path/to/dSYMs>`, hoặc
  - fastlane `upload_symbols_to_crashlytics`.

### 6.2 Kotlin/Native

- Framework `Shared` là **static**, nên debug info của nó nằm trong dSYM của app; không cần upload dSYM riêng.
- Exception Kotlin không được bắt sẽ kết thúc process qua `abort()`, và trên Crashlytics chỉ thấy stack của K/N runtime. Dùng **CrashKiOS** (`co.touchlab.crashkios:crashlytics`):
  - trong `iosMain` gọi `enableCrashlytics()` và `setCrashlyticsUnhandledExceptionHook()`;
  - từ Swift gọi qua một hàm `CrashKiOSBridge.install()` export từ shared, **sau** `FirebaseApp.configure()`.
- `Crash.install(FirebaseCrashReporter())` để `safeCall` hiện có ghi non-fatal lên Crashlytics, giống Android.
- `Crash.userId(InstallId)` dùng chung logic `getOrCreateInstallId` trong shared.

## 7. Ads: AdMob + UMP + ATT (chỉ khi quyết định D2 = có)

iOS v1 đề xuất **không** có ads. Phần này dùng khi bật ở v1.1.

Android đang trong quá trình gỡ AppLovin để chuyển sang AdMob (GMA Next-Gen SDK + UMP), xem [../plans/admob-migration.md](../plans/admob-migration.md). iOS **dùng lại nguyên** các phần sau của kế hoạch đó:

- bảng placement (§2.2);
- chính sách tần suất (§3.3);
- quy tắc không hiện full-screen ad khi audio đang phát, đang buffer, hoặc khi mở từ Now Playing/deep link (§0, §2.1);
- Remote Config key (§6).

Lưu ý: GMA Next-Gen SDK là SDK của Android. Trên iOS dùng **Google Mobile Ads SDK cho iOS** (SPM) cùng **UMP SDK cho iOS**. Khi tạo app iOS trong AdMob, phải gắn app với listing App Store để `app-ads.txt` được xác minh ([seo-web-dashboard.md §A.9](../plans/seo-web-dashboard.md)).

1. **AdMob console:** tạo app iOS và các ad unit (banner, native). **Không** ghi ad unit ID vào docs hay repo; đưa vào `Secrets.xcconfig`.
2. **Info.plist:**
   - `GADApplicationIdentifier`;
   - `SKAdNetworkItems`: danh sách identifier do Google cung cấp, cộng thêm của các mạng mediation;
   - `NSUserTrackingUsageDescription` (vi + en).
3. **Thứ tự khi app launch** (mỗi lần app trở về trạng thái active):

```swift
// 1) UMP: hỏi consent (GDPR/EEA/UK, các bang ở Mỹ) nếu cần
ConsentInformation.shared.requestConsentInfoUpdate(with: RequestParameters()) { _ in
    ConsentForm.loadAndPresentIfRequired(from: rootVC) { _ in
        guard ConsentInformation.shared.canRequestAds else { return }
        // 2) ATT: chỉ gọi khi app ĐÃ active (gọi sớm hơn có thể bị bỏ qua mà không hiện prompt)
        ATTrackingManager.requestTrackingAuthorization { _ in
            // 3) Khởi tạo SDK sau khi đã có kết quả consent và ATT
            MobileAds.shared.start()    // tên API khác nhau theo version SDK (v11: GADMobileAds.sharedInstance())
        }
    }
}
```

   - Có thể cấu hình **IDFA explainer message** trong AdMob → Privacy & messaging để UMP hiện màn giải thích ngay trước ATT.
   - Không chặn tính năng nào của app khi người dùng từ chối ATT (5.1.2: không được ép).
   - Tôn trọng `ads_enable` từ Remote Config, giống `AppOpenAdManager`. Chỉ hiện ad sau khi người dùng đã qua onboarding và chọn danh mục (Android: `adEnabled = isOnboardingFinished && hasSelectFavouriteCategory`).
4. **app-ads.txt:** đặt trên domain "Marketing/Support URL" của developer.
5. **App-open ad:**
   - tuân thủ 2.5.18 và 4.0 (không gây gián đoạn quá mức);
   - giữ ngưỡng ≥ 10 phút giữa hai lần hiện như Android (`showAdThreshHold`);
   - **không** hiện ngay lần mở app đầu tiên hoặc trong lúc reviewer đang đi qua onboarding.

## 8. Signing

| Mục | Thiết lập |
|---|---|
| Team | Apple Developer Program, loại Organization hoặc Individual (xem app-store-submission-plan.md §1) |
| App ID | `tss.t.podcast`. Capabilities: Push Notifications, (CarPlay Audio khi được cấp), App Groups (nếu làm widget hoặc Notification Service Extension cần chia sẻ dữ liệu) |
| Dev local | Automatic signing |
| Release / CI | **fastlane match** (lưu cert và profile đã mã hoá trong một private git repo), loại `appstore` và `development`, hoặc Xcode Cloud managed signing |
| App Store Connect API key | Users and Access → Integrations → Keys, role **App Manager**. Lưu `KEY_ID`, `ISSUER_ID` và nội dung `.p8` (base64) vào CI secrets. **Không** commit |
| Extension | Notification Service Extension (`tss.t.podcast.NotificationService`), Widget (`tss.t.podcast.Widget`). Mỗi extension cần profile riêng |

## 9. CI

### 9.1 GitHub Actions (macOS runner)

Runner `macos-26` đã GA trên GitHub Actions (theo release notes của runner-images, khoảng 4/2026). Image này có nhiều bản Xcode 26.x; chọn bản cụ thể bằng `xcode-select`. Lưu ý: với repo private, phút chạy macOS bị nhân hệ số cao hơn Linux, nên chỉ chạy job iOS khi có thay đổi trong `shared/**` hoặc `iosApp/**`.

`.github/workflows/ios.yml` (mẫu):

```yaml
name: iOS
on:
  pull_request:
    paths: ["shared/**", "iosApp/**", "gradle/**", "*.gradle.kts"]
  workflow_dispatch:
    inputs:
      lane: { description: "fastlane lane", default: "beta", type: choice, options: [test, beta] }

jobs:
  ios:
    runs-on: macos-26
    timeout-minutes: 60
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "17" }
      - uses: gradle/actions/setup-gradle@v4
      - uses: actions/cache@v4
        with:
          path: ~/.konan
          key: konan-${{ runner.os }}-${{ hashFiles('gradle/libs.versions.toml') }}
      - name: Select Xcode
        run: sudo xcode-select -s /Applications/Xcode_26.x.app    # thay bằng bản cụ thể
      - name: Shared tests
        run: ./gradlew :shared:allTests --no-daemon
      - name: Secrets → files
        env:
          GSP_RELEASE_B64: ${{ secrets.GOOGLE_SERVICE_INFO_RELEASE_B64 }}
          SECRETS_XCCONFIG_B64: ${{ secrets.IOS_SECRETS_XCCONFIG_B64 }}
        run: |
          mkdir -p iosApp/Config/Firebase/Release
          echo "$GSP_RELEASE_B64" | base64 -d > iosApp/Config/Firebase/Release/GoogleService-Info.plist
          echo "$SECRETS_XCCONFIG_B64" | base64 -d > iosApp/Config/Secrets.xcconfig
      - uses: ruby/setup-ruby@v1
        with: { ruby-version: "3.3", bundler-cache: true, working-directory: iosApp }
      - name: fastlane
        working-directory: iosApp
        env:
          ASC_KEY_ID: ${{ secrets.ASC_KEY_ID }}
          ASC_ISSUER_ID: ${{ secrets.ASC_ISSUER_ID }}
          ASC_KEY_P8_B64: ${{ secrets.ASC_KEY_P8_B64 }}
          MATCH_PASSWORD: ${{ secrets.MATCH_PASSWORD }}
          MATCH_GIT_BASIC_AUTHORIZATION: ${{ secrets.MATCH_GIT_BASIC_AUTHORIZATION }}
        run: bundle exec fastlane ios ${{ github.event.inputs.lane || 'test' }}
```

Nên dùng GitHub **Environments** để tách `testflight`/`production` (giống `GITHUB_ENVIRONMENTS_GUIDE.md` hiện có của Android).

### 9.2 Xcode Cloud (phương án thay thế)

- Gói Apple Developer Program có sẵn một lượng compute hours miễn phí mỗi tháng (kiểm tra lại hạn mức hiện tại).
- Workflow: Archive, rồi đẩy lên TestFlight (internal).
- Xcode Cloud **không có sẵn JDK**. Tạo `iosApp/ci_scripts/ci_post_clone.sh`:

```bash
#!/bin/sh
set -e
brew install --cask temurin@17 || true
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
cd "$CI_PRIMARY_REPOSITORY_PATH"
echo "$GSP_RELEASE_B64" | base64 -d > iosApp/Config/Firebase/Release/GoogleService-Info.plist
echo "$SECRETS_XCCONFIG_B64" | base64 -d > iosApp/Config/Secrets.xcconfig
./gradlew :shared:assembleSharedReleaseXCFramework --no-daemon   # nếu dùng cách B
```

- Secret khai báo ở Environment Variables của workflow (đánh dấu Secret).
- Upload dSYM lên Crashlytics trong `ci_post_xcodebuild.sh` bằng `upload-symbols`, với đường dẫn `$CI_ARCHIVE_PATH/dSYMs`.
- Nhược điểm: build Kotlin/Native khá chậm, và cache `~/.konan` không giữ lâu giữa các lần chạy. Với monorepo dùng cách A thì GitHub Actions dễ kiểm soát hơn.

## 10. fastlane

`iosApp/Gemfile`:

```ruby
source "https://rubygems.org"
gem "fastlane"
```

`iosApp/fastlane/Appfile`:

```ruby
app_identifier("tss.t.podcast")
```

`iosApp/fastlane/Fastfile`:

```ruby
default_platform(:ios)

platform :ios do
  before_all { setup_ci if is_ci }

  def asc_key
    app_store_connect_api_key(
      key_id: ENV["ASC_KEY_ID"], issuer_id: ENV["ASC_ISSUER_ID"],
      key_content: ENV["ASC_KEY_P8_B64"], is_key_content_base64: true)
  end

  lane :test do
    run_tests(project: "iosApp.xcodeproj", scheme: "TSPodcast",
              devices: ["iPhone 17"], result_bundle: true)
  end

  lane :beta do
    key = asc_key
    match(type: "appstore", readonly: true, api_key: key)
    increment_build_number(
      xcodeproj: "iosApp.xcodeproj",
      build_number: latest_testflight_build_number(api_key: key) + 1)
    build_app(project: "iosApp.xcodeproj", scheme: "TSPodcast",
              configuration: "Release", export_method: "app-store")
    upload_symbols_to_crashlytics(
      gsp_path: "Config/Firebase/Release/GoogleService-Info.plist")
    upload_to_testflight(api_key: key, skip_waiting_for_build_processing: true,
                         changelog: File.read("../distribution/whatsnew/whatsnew-vi") rescue nil)
  end

  lane :metadata do
    deliver(api_key: asc_key, skip_binary_upload: true, submit_for_review: false,
            metadata_path: "fastlane/metadata", screenshots_path: "fastlane/screenshots",
            precheck_include_in_app_purchases: false, force: true)
  end

  lane :screenshots do
    capture_screenshots(scheme: "TSPodcastUITests")   # snapshot: 6.9", 6.1" (+13" iPad nếu bật)
  end
end
```

- Metadata (`fastlane/metadata/vi/`, `fastlane/metadata/en-US/`) gồm `name.txt`, `subtitle.txt`, `description.txt`, `keywords.txt`, `privacy_url.txt`, `support_url.txt`, `release_notes.txt`. Nội dung đề xuất ở app-store-submission-plan.md §12.
- Thư mục `distribution/whatsnew` của Android đã có sẵn và có thể dùng lại cho release notes.

## 11. Đánh version

- `MARKETING_VERSION` (`CFBundleShortVersionString`): bắt đầu từ `1.0.0` trên iOS, độc lập với Android (`v3.3.10016`).
- `CURRENT_PROJECT_VERSION` (`CFBundleVersion`): tăng tự động (lane `beta`). Mỗi lần upload phải là số mới.
