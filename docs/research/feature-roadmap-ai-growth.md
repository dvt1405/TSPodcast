# Lộ trình tính năng: AI, tăng trưởng và tương tác (TSPodcast)

> Ngày lập: 29/10/2026 · Đi kèm: `docs/research/competitor-analysis.md`,
> `docs/policy/store-policy-review.md`.
>
> Mọi chi phí là **ước tính kỹ thuật** dựa trên giá công bố tại thời điểm viết (có link). Chúng
> dùng để so sánh các phương án với nhau, không phải để lập ngân sách. Cần đo lại bằng dữ liệu
> thật (Firebase Analytics, dashboard quảng cáo) trước khi cam kết.

---

## 0. Tiền đề kiến trúc

1. **Hiện chưa có backend.** App gọi thẳng PodcastIndex, khoá API nằm trong `securedToken`
   (thư viện native). Mọi tính năng AI dùng API trả phí **bắt buộc có backend**, ví dụ Cloud
   Run hoặc Cloud Functions trong project Firebase đang có. Không được nhúng khoá LLM hay STT
   vào APK hoặc IPA, vì khoá native vẫn trích xuất được. Phương án tối thiểu: Cloud Run
   (Kotlin/Ktor để dùng chung model với KMP) + Firestore hoặc Cloud SQL (Postgres + pgvector)
   + Cloud Tasks cho job nền, bảo vệ bằng **Firebase App Check**. Chi phí cố định ước tính
   khoảng $10–50/tháng ở quy mô nhỏ **(ước tính, chưa xác minh)**.
2. **Chi phí AI tỉ lệ theo số tập được xử lý, không theo số người dùng.** Transcript và tóm tắt
   của một tập dùng chung được cho mọi người nghe tập đó. Đây là đòn bẩy kinh tế lớn nhất.
   Snipd cũng giới hạn theo "số tập/phút AI" vì lý do này.
3. **Ưu tiên dữ liệu có sẵn trước, AI sau.** Thứ tự: transcript/chapters do creator khai báo
   (`podcast:transcript`, `podcast:chapters` trong RSS; Apple đã dùng chuẩn transcript từ 2024,
   [forum](https://forum.pierobosio.it/tags/podcasting)) → cache dùng chung → AI theo yêu cầu.
4. **Lưu ý điều khoản PodcastIndex.** Commit `dddd818` đã áp dụng điều 5.5(1) (không lưu bản sao
   vĩnh viễn nội dung API) và 7.2 (ghi công). Transcript do mình tạo từ **audio** không phải
   "nội dung API", nhưng là **sản phẩm phái sinh từ tác phẩm có bản quyền của creator**. Cần
   có chính sách lưu trữ và gỡ bỏ (xem tài liệu policy, mục 2).

### Giả định để tính chi phí "trên 1.000 MAU / tháng"

| Tham số | Giá trị giả định |
|---|---|
| Tỉ lệ MAU dùng tính năng AI | 25% (250 người) |
| Số tập mỗi người dùng AI yêu cầu xử lý/tháng | 4 |
| Tỉ lệ tập đã được người khác xử lý (cache hit) | 30%, nên có **700 tập duy nhất/tháng** |
| Độ dài trung bình một tập | 50 phút, nên có **35.000 phút audio/tháng** |
| Số token của transcript một tập 50 phút | ~10k (EN) đến ~20k (VI, tiếng Việt tốn token hơn). Dùng 20k để tính |

Giá dùng trong tính toán:
- STT đám mây: `gpt-4o-mini-transcribe` khoảng **$0.003/phút**. Deepgram Nova-3 khoảng
  **$0.0043–0.009/phút** tuỳ gói và đơn hay đa ngôn ngữ
  ([convertaudiototext](https://convertaudiototext.com/blog/speech-to-text-api-pricing-2026),
  [OpenRouter so sánh](https://openrouter.ai/compare/deepgram/nova-3/openai/gpt-4o-mini-transcribe),
  [Deepgram pricing 2026](https://www.activepieces.com/blog/deepgram-pricing-2026-per-minute-rates-and-add-ons.md)).
  Chất lượng tiếng Việt **chưa được kiểm chứng**, cần benchmark.
- LLM: **Claude Haiku 5.5**: $0.10 / 1M token vào, $0.50 / 1M token ra (prompt đến 100k token).
  Message Batches API giảm 50%. Prompt caching giảm thêm phần đọc lại. **Claude Sonnet 5.5**:
  $2 / $10 (bảng giá model Anthropic, cập nhật 06/10/2026).
- Lồng tiếng: ElevenLabs Dubbing v1 khoảng **$0.33/phút nguồn** (có watermark) đến **$0.50**
  (không watermark). Dubbing v2 khoảng **$2.20/phút**
  ([puter.com](https://developer.puter.com/tutorials/elevenlabs-api-pricing/),
  [usagepricing](https://www.usagepricing.com/blueprint/activity/elevenlabs-2026-09-28-v3-conversational-dubbing-v2)).
  **(Nguồn thứ cấp)**

---

## (a) Kế hoạch tính năng AI

Thang ưu tiên: **P0** làm ngay · **P1** trong 6 tháng · **P2** 6–12 tháng · **P3** thử nghiệm
hoặc chờ.

### A1. Transcript (bản ghi lời)

| | |
|---|---|
| **Giá trị** | Đọc lướt, tìm kiếm, trợ năng (người khiếm thính), học tiếng Anh. Là nền cho A2–A5. Đối thủ đều đã có (Apple, Pocket Casts Plus, Overcast 2026.04, Snipd) |
| **Cách làm** | (1) Đọc `podcast:transcript` (SRT/VTT/JSON) từ feed: miễn phí. (2) Cloud STT theo yêu cầu: người dùng bấm "Tạo bản ghi", backend tải audio từ enclosure URL, gọi STT, lưu kết quả có TTL. (3) Thử nghiệm on-device: whisper.cpp + **PhoWhisper** (VinAI, fine-tune trên 844 giờ tiếng Việt, [HF](https://huggingface.co/vinai/PhoWhisper-base/blob/main/README.md?code=true), [arXiv](https://arxiv.deeppaper.ai/papers/2406.02555v1)). Chưa có benchmark tốc độ PhoWhisper trên Android. Bản base hoặc small có thể chạy được nhưng 50 phút audio sẽ tốn pin. ML Kit GenAI Speech Recognition (Gemini Nano) mới ở alpha, chế độ Advanced chỉ có trên Pixel 10, và dành cho micro chứ không cho file ([Android Dev](https://developer.android.com/ai/gemini-nano/ml-kit-genai?authuser=1)) |
| **Chi phí / 1k MAU** | Cloud: 35.000 phút × $0.003 ≈ **$105** (gpt-4o-mini-transcribe), hoặc **$150–315** (Deepgram). Nếu 30% tập có transcript của creator thì giảm khoảng 30%. On-device: **$0** server, đổi lại tốn pin/dung lượng và chất lượng chưa rõ |
| **Giảm chi phí** | Chỉ xử lý khi có người bấm. Giới hạn miễn phí (ví dụ 3 tập/tháng) như Pocket Casts/Snipd. Xử lý trước top 100 tập trending VN mỗi ngày. Tách nhạc nền và khoảng lặng trước khi gửi |
| **Rủi ro quyền riêng tư / chính sách** | Audio là nội dung công khai, không phải dữ liệu cá nhân. Rủi ro chính là **bản quyền**: lưu và hiển thị toàn văn tác phẩm. Cần nhãn "Bản ghi tự động, có thể sai", nút báo lỗi, và gỡ khi creator yêu cầu. Gửi audio ra nhà cung cấp nước ngoài thì không có dữ liệu người dùng đi kèm |
| **Ưu tiên** | Đọc transcript của creator: **P0**. Cloud STT theo yêu cầu: **P1**. Thử on-device: **P3** |

### A2. Tóm tắt AI + chapters AI

| | |
|---|---|
| **Giá trị** | "Tập này nói gì?" trong 20 giây giúp tăng tỉ lệ bấm nghe. Chapters giúp nhảy tới đoạn quan tâm. Apple đã có chapters tự tạo bằng AI (iOS 26.2) |
| **Cách làm** | Từ transcript, gọi **Claude Haiku 5.5** qua Batch API với structured output: `{tóm tắt 3 câu, 5 ý chính, chapters[{start, title}], chủ đề, mức explicit}`. Ngôn ngữ đầu ra theo ngôn ngữ UI (VN), kể cả với tập tiếng Anh. Lưu cùng transcript |
| **Chi phí / 1k MAU** | Mỗi tập: 20k token × $0.10/M + 1,5k token × $0.50/M ≈ **$0.003**. Với 700 tập ≈ **$2**, còn khoảng **$1** nếu dùng Batch. Nếu dùng Sonnet 5.5 thì khoảng $0.055/tập, ≈ $38 |
| **Rủi ro** | Tóm tắt sai hoặc bịa (hallucination). Cần nhãn "Do AI tạo" và nút báo cáo. Theo trang chính sách của Google Play, app **chỉ tóm tắt nội dung không do AI tạo nằm ngoài phạm vi** AI-Generated Content policy ([Play Help](https://support.google.com/googleplay/android-developer/answer/14094294?hl=en)). Nên vẫn làm cơ chế báo cáo để phòng xa |
| **Ưu tiên** | **P1**, đi cùng A1 |

### A3. "Hỏi tập này" (Ask this episode)

| | |
|---|---|
| **Giá trị** | Ngang Spotify (chỉ US/SE/IE) và Snipd (không có tiếng Việt). Hỏi bằng tiếng Việt về một tập tiếng Anh là điểm khác biệt mạnh |
| **Cách làm** | Đưa toàn bộ transcript (≤100k token) vào context của Claude Haiku 5.5, có **prompt caching** cho phần transcript. Trả lời kèm mốc thời gian có thể chạm để nhảy tới. Không cần vector DB cho một tập. System prompt: chỉ trả lời dựa trên transcript, không biết thì nói không biết. Giới hạn số câu hỏi/ngày |
| **Chi phí / 1k MAU** | Giả định 10% MAU × 10 câu = 1.000 câu × (22k vào + 300 ra) ≈ **$2.4**, còn thấp hơn nhiều khi cache hit |
| **Rủi ro** | Câu hỏi của người dùng là **dữ liệu cá nhân gửi sang bên thứ ba (AI)**. Apple 5.1.2(i) bản 11/2025 yêu cầu **công bố rõ và xin phép rõ ràng** trước khi chia sẻ dữ liệu cá nhân với "third-party AI" ([TechCrunch](https://techcrunch.com/2025/11/13/apples-new-app-review-guidelines-clamp-down-on-apps-sharing-personal-data-with-third-party-ai)). Luật BVDLCN 91/2025 có hiệu lực 1/1/2026 ([LuatVietnam](https://luatvietnam.vn/dan-su/luat-bao-ve-du-lieu-ca-nhan-2025-co-hieu-luc-khi-nao-568-103652-article.html)). Chatbot dạng text-to-text có thể bị coi thuộc phạm vi AI policy của Play, nên cần: lọc prompt, báo cáo câu trả lời, không giữ log quá mức cần thiết |
| **Ưu tiên** | **P1** (sau A2) |

### A4. Clip và highlight thông minh (kiểu Snipd)

| | |
|---|---|
| **Giá trị** | Người dùng lưu "đoạn hay" thành ghi chú, và quan trọng hơn là **chia sẻ clip** lên Zalo/Facebook/TikTok, tạo vòng tăng trưởng (xem B1) |
| **Cách làm** | (1) Thủ công: nút "Lưu đoạn này" lấy 30–60 giây trước vị trí hiện tại; có thể dùng nút tai nghe (MediaSession custom command). (2) AI: LLM chọn 3–5 đoạn đáng trích từ transcript (kèm A2, gần như không tốn thêm). (3) Xuất video dọc trên máy bằng **Media3 Transformer**: ảnh bìa + sóng âm + phụ đề từ transcript + watermark và deep link |
| **Chi phí / 1k MAU** | Gần **$0** thêm (render trên máy; gợi ý highlight đã nằm trong prompt A2) |
| **Rủi ro** | Trích đoạn tác phẩm có bản quyền: giới hạn ≤60 giây, luôn ghi tên show và link gốc, tôn trọng yêu cầu gỡ. Không cho tải nguyên tập dưới dạng file |
| **Ưu tiên** | Lưu đoạn thủ công + chia sẻ: **P1**. Gợi ý AI: **P2** |

### A5. Tìm kiếm ngữ nghĩa

| | |
|---|---|
| **Giá trị** | "podcast về khởi nghiệp nông nghiệp ở miền Tây" sẽ cho ra tập phù hợp dù tiêu đề không chứa từ khoá. Tìm cả trong lời nói (transcript) |
| **Cách làm** | Bước 1 (rẻ): chuẩn hoá tiếng Việt bỏ dấu, sửa lỗi gõ, gợi ý. Bước 2: embedding đa ngôn ngữ (ví dụ mô hình mở như multilingual-e5 hoặc bge-m3 tự host, hoặc API embedding) cho tiêu đề + mô tả + tóm tắt A2, lưu trong pgvector. Kết hợp với kết quả `search/byterm` của PodcastIndex. Lưu ý điều 5.5 của PodcastIndex: index metadata phải có TTL và làm mới, không lưu vĩnh viễn |
| **Chi phí / 1k MAU** | Phần biến đổi gần **$0–1**. Chi phí chính là hosting vector DB cố định (ước tính $20–50/tháng, **chưa xác minh**) |
| **Rủi ro** | Thấp. Log truy vấn tìm kiếm là dữ liệu cá nhân, cần ẩn danh và giới hạn thời gian lưu |
| **Ưu tiên** | Chuẩn hoá tiếng Việt: **P0**. Embedding: **P2** |

### A6. Gợi ý cá nhân hoá

| | |
|---|---|
| **Giá trị** | Màn Discover hiện dựa trên trending và thể loại onboarding. Gợi ý tốt là yếu tố giữ chân số 1 của Spotify/YouTube |
| **Cách làm** | Giai đoạn 1 (on-device, không cần backend): điểm số theo thể loại đã nghe, thời lượng nghe, show theo dõi, giờ trong ngày (sáng thì radio/tin tức, tối thì thư giãn). Giai đoạn 2: co-listening ("người nghe X cũng nghe Y") qua Firebase → BigQuery export, cập nhật hằng đêm. Giai đoạn 3: "Prompted discovery" kiểu Spotify ("gợi ý 5 tập cho 30 phút đi làm, chủ đề tài chính"), LLM chọn từ danh sách ứng viên |
| **Chi phí / 1k MAU** | Giai đoạn 1–2: **khoảng $0–5**. Giai đoạn 3: mỗi prompt khoảng 5k token, ≈ $0.0006 |
| **Rủi ro** | Data safety/App Privacy phải khai "lịch sử nghe dùng để cá nhân hoá". Cần nút tắt cá nhân hoá |
| **Ưu tiên** | Giai đoạn 1: **P1**. Giai đoạn 2: **P2**. Giai đoạn 3: **P2** |

### A7. Dịch và lồng tiếng AI (VN↔EN)

| | |
|---|---|
| **Giá trị** | Người Việt nghe được podcast tiếng Anh hay; creator Việt tiếp cận khán giả quốc tế. Đây là câu chuyện "cầu nối" độc đáo cho truyền thông |
| **Cách làm** | Cấp 1 (rẻ): **dịch transcript** song ngữ hiển thị song song, rất hữu ích cho người học tiếng Anh; Haiku khoảng **$0.012/tập**. Cấp 2: tóm tắt audio tiếng Việt khoảng 5 phút cho tập tiếng Anh (TTS). Cấp 3: lồng tiếng toàn tập (ElevenLabs Dubbing **$16–25/tập** 50 phút với v1, khoảng **$110** với v2) |
| **Chi phí / 1k MAU** | Cấp 1: khoảng **$3** (ví dụ 250 tập). Cấp 3: không tính theo MAU mà là **ngân sách biên tập cố định**, ví dụ 20 tập/tháng ≈ $330–500 |
| **Rủi ro** | **Cao.** Dịch hoặc lồng tiếng là tạo **tác phẩm phái sinh**, cần sự đồng ý của chủ sở hữu. Lồng tiếng bằng giọng của người thật (host) nằm trong phạm vi Play AI-Generated Content policy ("AI voice recordings of real people") và có rủi ro deepfake. Audible chỉ làm với nhà xuất bản đã đồng ý, và một bộ phận người nghe phản đối giọng AI |
| **Ưu tiên** | Cấp 1: **P2**. Cấp 2: **P3**. Cấp 3: **P3, chỉ với creator đã ký đồng ý** |

### A8. Bản tin AI hằng ngày

| | |
|---|---|
| **Giá trị** | Lý do mở app mỗi sáng (giống Spotify Personal Podcasts / Daily briefing). Kết hợp tốt với radio buổi sáng |
| **Cách làm** | **Bản tin dùng chung theo chủ đề** (Công nghệ, Kinh doanh, Học tiếng Anh…), không làm riêng cho từng người. Nguồn: **các tập podcast mới trong 24 giờ** của chủ đề đó (đã có tóm tắt A2). Không dùng bài báo, để tránh vấn đề bản quyền báo chí. LLM viết kịch bản 3–5 phút có trích dẫn tập gốc, rồi TTS giọng Việt. Phát như một "episode" trong app, cuối bản tin có link tới tập gốc (đẩy traffic cho creator) |
| **Chi phí** | Không tỉ lệ theo MAU: 5 chủ đề × 30 ngày = 150 bản/tháng. LLM khoảng $0.005/bản. TTS khoảng 5k ký tự/bản, **ước tính $0.02–0.08/bản** (giá TTS neural các nhà cung cấp lớn, **cần kiểm tra lại**). Tổng khoảng **$4–15/tháng** |
| **Rủi ro** | Nội dung tổng hợp bằng AI: ghi nhãn rõ "Bản tin do AI tổng hợp". Tránh tin chính trị và thời sự nhạy cảm ở VN (Nghị định 147/2024 về thông tin trên mạng). Có quy trình biên tập/duyệt trước khi phát |
| **Ưu tiên** | **P2** |

### A9. Điều khiển bằng giọng nói

| | |
|---|---|
| **Giá trị** | Khi lái xe hay chạy bộ: "Mở VOV Giao thông", "Tua lại 30 giây" |
| **Cách làm** | Không cần AI riêng: hỗ trợ `MediaLibraryService` + `onPlayFromSearch` để Google Assistant/Gemini và Android Auto gọi được (chuẩn VC-1 trong car app quality guidelines, [Android for Cars](https://developer.android.com/training/cars/media?hl=nl)). Trong app có thể thêm nút micro dùng `SpeechRecognizer` hoặc ML Kit (Basic mode, on-device) |
| **Chi phí** | **$0** |
| **Rủi ro** | Thấp. Nếu ghi âm micro thì phải xin quyền `RECORD_AUDIO` và khai Data safety |
| **Ưu tiên** | **P1** (đi cùng Android Auto) |

### Tổng hợp chi phí AI (1.000 MAU / tháng, theo giả định trên)

| Gói tính năng | Chi phí biến đổi | Ghi chú |
|---|---|---|
| Transcript creator + tóm tắt/chapters (A1 creator + A2) | khoảng $1–2 | Rất rẻ, làm ngay |
| + Transcript AI theo yêu cầu (A1 cloud) | +$105 (đến $315) | Khoản lớn nhất, **cần giới hạn hoặc thu phí** |
| + Hỏi tập này (A3) | +$2–3 | |
| + Dịch transcript (A7 cấp 1) | +$3 | |
| Bản tin AI (A8) | $4–15 cố định | |
| Backend | $10–50 cố định | |

**Kết luận:** mọi tính năng **dựa trên văn bản** (tóm tắt, hỏi đáp, dịch) đều rẻ với Haiku 5.5.
**Speech-to-text là nút thắt chi phí.** Nên đi theo thứ tự: transcript của creator → cache
dùng chung → xử lý trước top tập VN → giới hạn miễn phí → gói Premium cho người dùng nặng. Doanh
thu quảng cáo/MAU ở VN cần lấy số thật từ dashboard AppLovin/AdMob để đặt trần chi phí AI.
Gợi ý: giữ chi phí AI ≤ 30% doanh thu/MAU.

---

## (b) Tính năng tăng trưởng và tương tác

| # | Tính năng | Mục tiêu | Cách làm / ghi chú | Ưu tiên |
|---|---|---|---|---|
| G1 | **Chia sẻ clip và trích dẫn** | Thu hút người dùng mới | A4 + deep link `https://<domain>/e/<id>?t=123`. Trang web nhẹ cho người chưa cài app (về sau thành bản web). Thẻ OG có ảnh bìa | P1 |
| G2 | **Widget** (Glance) | Giữ chân | Widget "Đang phát" + "Radio yêu thích" (bấm một lần là phát) + "Tập mới" | P1 |
| G3 | **Streak và thống kê nghe**, **"TSPodcast Wrapped"** cuối năm | Giữ chân + lan truyền | Lưu phút nghe theo ngày trong Room. Mốc 7/30/100 ngày. Wrapped: top show, tổng giờ, giờ nghe nhiều nhất, xuất ảnh 9:16 để chia sẻ. Ra **giữa tháng 12** | P1 (stats), mùa Wrapped |
| G4 | **Hẹn giờ ngủ** | Giữ chân (nghe trước khi ngủ, rất phổ biến với radio và truyện) | Theo phút hoặc hết tập, fade-out, lắc máy để gia hạn | **P0** |
| G5 | **Android Auto** (sau đó CarPlay khi có iOS) | Mở rộng use case | `MediaLibraryService`, cây duyệt ≤4 cấp, tìm kiếm bằng giọng nói. Radio trên xe ô tô là use case mạnh ở VN. Review theo car app quality guidelines | P1 |
| G6 | **Wear OS** | Ngách | Đồng hồ điều khiển media session miễn phí (đã có). App Wear riêng để tải tập về đồng hồ cho chạy bộ: để sau | P3 |
| G7 | **Tải offline** | Giữ chân | Media3 `DownloadService`, tự tải khi có Wi-Fi + sạc, tự xoá tập đã nghe, giới hạn dung lượng. Đây là thứ bị phàn nàn nhiều nhất nếu thiếu | **P0/P1** |
| G8 | **Xã hội / bình luận** | Cộng đồng | Bắt đầu bằng **cảm xúc theo mốc thời gian** (reaction, không có văn bản) để tránh UGC. Bình luận văn bản chỉ làm khi đã có đăng nhập + kiểm duyệt + đáp ứng Nghị định 147 (xác thực số điện thoại VN) | P3 |
| G9 | **Giới thiệu bạn bè** | Thu hút | Play Install Referrer + deep link. Phần thưởng: X ngày Premium (khi có Premium) hoặc huy hiệu. Không trả tiền mặt hay quà có giá trị (rủi ro "incentivized installs") | P2 |
| G10 | **Onboarding** | Kích hoạt | Hiện có chọn thể loại. Thêm: chọn 3 show cụ thể (lưới ảnh bìa show VN nổi bật), "Nghe radio ngay" một chạm, nhập OPML. Mục tiêu: **lần nghe đầu tiên trong 60 giây** | P1 |
| G11 | **Thông báo** | Kéo người dùng quay lại | Xin quyền `POST_NOTIFICATIONS` **theo ngữ cảnh** (khi người dùng bấm theo dõi show, không xin ngay lần mở đầu). Loại thông báo: tập mới của show theo dõi, nhắc "nghe tiếp", bản tin sáng (A8). Giới hạn ≤1 thông báo marketing/ngày, có kênh riêng cho từng loại | P1 |
| G12 | **ASO** | Thu hút | Từ khoá VN: "nghe podcast", "nghe radio", "nghe đài VOV", "radio FM online", "podcast tiếng Việt", "học tiếng Anh qua podcast". Không dùng tên thương hiệu đài nếu chưa có thoả thuận. Listing tiếng Anh riêng cho quốc tế. Dùng **Store listing experiments** của Play Console để A/B test icon và screenshot. Trả lời review trong 48 giờ. Mục tiêu ≥4.5★ | P0 (liên tục) |
| G13 | **Quảng cáo ít gây khó chịu** | Giữ chân + đánh giá | Sửa app-open ad (xem tài liệu policy). Không chèn quảng cáo video có tiếng khi đang phát audio. Native ad trong danh sách được. Ước tính đây là việc có tác động lớn nhất tới rating | **P0** |

---

## (c) Lộ trình theo giai đoạn

### Giai đoạn 1: 0–3 tháng (11/2026 → 01/2027) · "Chơi được trò chơi cơ bản"

| Hạng mục | Nội dung |
|---|---|
| **Tuân thủ (chặn release)** | Sửa logic app-open ad. Chuyển sang AdMob + UMP consent. Khai Data safety đúng (Firebase, AppLovin/AdMob, Facebook SDK). Bỏ gửi ANDROID_ID vào Analytics. Thêm link chính sách quyền riêng tư trong app. Gắn nhãn/lọc explicit. Gửi văn bản xin phép VOV/VOH (xem tài liệu policy) |
| **Player cơ bản** | Tốc độ, bỏ khoảng lặng, hẹn giờ ngủ, nhớ vị trí, "Tiếp tục nghe" |
| **Nội dung** | Đọc transcript/chapters của creator. Tìm kiếm không dấu |
| **Tăng trưởng** | Deep link + chia sẻ tập. ASO tiếng Việt. Thông báo theo ngữ cảnh |
| **Hạ tầng** | Dựng backend tối thiểu (Cloud Run + App Check), chưa bật AI |
| **KPI cuối giai đoạn** | Crash-free users ≥ 99,5% · Rating Play ≥ 4.4 · Retention D1 ≥ 30%, D7 ≥ 12% · Phút nghe/DAU +20% so với baseline · Tỉ lệ cài mới đến lần phát đầu tiên ≥ 60% |

### Giai đoạn 2: 3–6 tháng (02 → 04/2027) · "Giữ chân và khác biệt"

| Hạng mục | Nội dung |
|---|---|
| **Giữ chân** | Tải offline + tự tải. Theo dõi show + thông báo tập mới. Widget. Thống kê và streak. Android Auto + điều khiển giọng nói |
| **AI v1** | Tóm tắt + chapters AI (Haiku 5.5, Batch). Transcript AI theo yêu cầu, giới hạn N tập/tháng miễn phí. Xử lý trước top 100 tập VN |
| **Radio** | Danh mục kênh có thoả thuận, lịch phát sóng, báo thức bằng radio |
| **Tăng trưởng** | Lưu và chia sẻ clip (render video trên máy). Cải thiện onboarding |
| **KPI** | D7 ≥ 15%, D30 ≥ 7% · DAU/MAU ≥ 20% · ≥ 25% MAU theo dõi ít nhất 1 show · ≥ 15% MAU dùng ít nhất 1 tính năng AI/tháng · Chi phí AI ≤ $0.15/MAU/tháng · ≥ 2% người nghe chia sẻ tập hoặc clip/tháng · Opt-in thông báo ≥ 50% |

### Giai đoạn 3: 6–12 tháng (05 → 10/2027) · "Mở rộng"

| Hạng mục | Nội dung |
|---|---|
| **AI v2** | "Hỏi tập này". Tìm kiếm ngữ nghĩa trên transcript. Dịch transcript song ngữ VN↔EN. Bản tin AI hằng ngày theo chủ đề. Gợi ý co-listening |
| **Kiếm tiền** | Gói **Premium** (không quảng cáo + AI không giới hạn + tải không giới hạn) qua Play Billing / StoreKit |
| **Đa nền tảng** | iOS (KMP chia sẻ logic, UI native hoặc Compose MP) với khác biệt rõ để qua guideline 4.3. Bản web cho trang chia sẻ và player cơ bản |
| **Cộng đồng** | Reaction theo mốc thời gian, playlist công khai. Bình luận chỉ làm khi đã sẵn sàng pháp lý |
| **Thử nghiệm** | Lồng tiếng AI với 1–3 creator Việt đã ký đồng ý. Thử transcript on-device bằng PhoWhisper |
| **Mùa Wrapped** | Ra 12/2026 (bản tối giản từ dữ liệu giai đoạn 1) và 12/2027 (bản đầy đủ) |
| **KPI** | MAU ×3 so với cuối giai đoạn 1 · D30 ≥ 10% · Tỉ lệ chuyển đổi Premium ≥ 1,5% MAU · Doanh thu/MAU (ads + sub) ≥ 2× chi phí hạ tầng + AI/MAU · iOS rating ≥ 4.5 · ≥ 5% người dùng mới đến từ link chia sẻ hoặc giới thiệu |

### KPI theo dõi xuyên suốt (Firebase Analytics + BigQuery export)

- **Kích hoạt:** cài mới → lần phát đầu tiên; thời gian tới lần phát đầu.
- **Tương tác:** phút nghe/DAU; số phiên/ngày; tỉ lệ nghe hết tập; tỉ lệ podcast/radio/nhạc.
- **Giữ chân:** D1/D7/D30; cohort theo nguồn cài đặt.
- **AI:** % MAU dùng AI; số tập xử lý/tháng; cache hit rate; **chi phí AI/MAU**; tỉ lệ báo lỗi
  AI; CSAT (👍/👎) cho tóm tắt và câu trả lời.
- **Tăng trưởng:** lượt chia sẻ/người nghe; tỉ lệ cài từ deep link; hệ số K của referral.
- **Kiếm tiền:** ARPDAU quảng cáo; % phiên có app-open ad; tỉ lệ chuyển đổi Premium.
- **Chất lượng:** crash-free users; ANR rate (≤ 0,47% theo ngưỡng Android vitals); rating và
  số review 1★ nhắc tới "quảng cáo".

---

## Phụ lục: nguồn

- STT: [convertaudiototext 2026](https://convertaudiototext.com/blog/speech-to-text-api-pricing-2026) · [OpenRouter](https://openrouter.ai/compare/deepgram/nova-3/openai/gpt-4o-mini-transcribe) · [Deepgram pricing 2026](https://www.activepieces.com/blog/deepgram-pricing-2026-per-minute-rates-and-add-ons.md)
- On-device: [ML Kit GenAI](https://developer.android.com/ai/gemini-nano/ml-kit-genai?authuser=1) · [PhoWhisper](https://huggingface.co/vinai/PhoWhisper-base/blob/main/README.md?code=true) · [whisper.cpp Android](https://dev.to/software_mvp-factory/wiring-whispercpp-to-androids-audiorecord-api-hjo)
- Lồng tiếng: [ElevenLabs API pricing](https://developer.puter.com/tutorials/elevenlabs-api-pricing/) · [usagepricing](https://www.usagepricing.com/blueprint/activity/elevenlabs-2026-09-28-v3-conversational-dubbing-v2)
- LLM: bảng giá model Anthropic (Claude Haiku 5.5 $0.10/$0.50, Sonnet 5.5 $2/$10 per MTok; Batch giảm 50%), xem tại platform.claude.com/docs/en/about-claude/pricing
- Chính sách AI: [Google Play AI-Generated Content](https://support.google.com/googleplay/android-developer/answer/14094294?hl=en) · [Apple 5.1.2(i) 11/2025](https://techcrunch.com/2025/11/13/apples-new-app-review-guidelines-clamp-down-on-apps-sharing-personal-data-with-third-party-ai)
- Đối thủ (Spotify, YouTube, Snipd…): xem `docs/research/competitor-analysis.md`
