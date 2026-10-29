# Handoff iOS — việc cần owner

Danh sách những việc agent không làm thay được, hoặc đã phải tự quyết thay owner trong lúc xây app iOS. Mỗi mục ghi rõ: việc gì, vì sao bị chặn, trong lúc chờ đã làm gì, file liên quan. Agent cập nhật file này sau mỗi milestone; mục nào đã xử lý thì xoá.

Tiến độ chi tiết: [progress.md](progress.md). Kế hoạch và biên bản nghiệm thu từng milestone: [milestones/](milestones/).

## Cần owner xử lý / quyết định

### Tài khoản và cloud resource

- **Firebase iOS app** (bundle `tss.t.podcast`) và file `GoogleService-Info.plist` (setup-and-build §4).
  - Bị chặn vì: agent không được tạo cloud resource.
  - Trong lúc chờ: Firebase trên iOS là tuỳ chọn. Remote Config chưa có nên blacklist dùng asset đóng gói (`shared` sinh từ `coreApi/src/main/assets/blacklist.json`). Analytics, Crashlytics, FCM chưa chạy.
  - Khi có: implement `ConfigAPI` bằng Firebase và gọi `graph.blacklistRepository.reload()` sau `fetchAndActivate` (progress.md, mục M0 T3).
- **Key PodcastIndex riêng cho iOS** (architecture §5 mục 3).
  - Hiện tại: `iosApp/Config/Secrets.xcconfig` (gitignored) dùng chung key/secret với Android, chép từ `securedtoken.cpp`.
  - Nên cấp key riêng để thu hồi độc lập và tách quota trước khi lên TestFlight.

### Quyết định agent đã tự chọn (owner xác nhận hoặc đổi)

- **Search gửi ít tham số hơn Android:** E7/E8 không gửi `pretty=false`, E8 không gửi `aponly=false`; E11 không retry `SerializationException`. PO M0 đánh giá không đổi dữ liệu trả về (progress.md, M0 T2).
- **Kotlin giữ 2.2.0, không dùng SKIE**, tích hợp framework bằng cách A (`embedAndSignAppleFrameworkForXcode`). Hệ quả: mỗi lần build Xcode đều chạy lại bước Gradle, build tăng dần chậm hơn (M0 review).
