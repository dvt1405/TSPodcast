package tss.t.featureonboarding

/**
 * Vietnamese names of the PodcastIndex categories (`categories.json` ids), so the topic picker
 * reads naturally (iOS+ "bản địa hoá tên danh mục"). The English name stays the search key.
 */
object CategoryNames {
    private val vi = mapOf(
        1 to "Nghệ thuật", 2 to "Sách", 3 to "Thiết kế", 4 to "Thời trang", 5 to "Làm đẹp",
        6 to "Ẩm thực", 7 to "Biểu diễn", 8 to "Nghệ thuật thị giác", 9 to "Kinh doanh",
        10 to "Sự nghiệp", 11 to "Khởi nghiệp", 12 to "Đầu tư", 13 to "Quản lý", 14 to "Marketing",
        15 to "Phi lợi nhuận", 16 to "Hài", 17 to "Phỏng vấn", 18 to "Ứng biến", 19 to "Hài độc thoại",
        20 to "Giáo dục", 21 to "Khoá học", 22 to "Hướng dẫn", 23 to "Ngoại ngữ", 24 to "Học tập",
        25 to "Phát triển bản thân", 26 to "Truyện hư cấu", 27 to "Kịch", 28 to "Lịch sử",
        29 to "Sức khoẻ", 30 to "Thể hình", 31 to "Y học thay thế", 32 to "Y học", 33 to "Sức khoẻ tinh thần",
        34 to "Dinh dưỡng", 35 to "Giới tính", 36 to "Trẻ em", 37 to "Gia đình", 38 to "Nuôi dạy con",
        39 to "Thú cưng", 40 to "Động vật", 41 to "Kể chuyện", 42 to "Giải trí thư giãn", 43 to "Hoạt hình",
        44 to "Manga", 45 to "Ô tô, xe máy", 46 to "Hàng không", 47 to "Thủ công", 48 to "Trò chơi",
        49 to "Sở thích", 50 to "Nhà cửa", 51 to "Làm vườn", 52 to "Game điện tử", 53 to "Âm nhạc",
        54 to "Bình luận", 55 to "Tin tức", 56 to "Tin hằng ngày", 57 to "Giải trí", 58 to "Chính phủ",
        59 to "Chính trị", 60 to "Phật giáo", 61 to "Kitô giáo", 62 to "Ấn Độ giáo", 63 to "Hồi giáo",
        64 to "Do Thái giáo", 65 to "Tôn giáo", 66 to "Tâm linh", 67 to "Khoa học", 68 to "Thiên văn",
        69 to "Hoá học", 70 to "Khoa học Trái Đất", 71 to "Khoa học sự sống", 72 to "Toán học",
        73 to "Khoa học tự nhiên", 74 to "Thiên nhiên", 75 to "Vật lý", 76 to "Khoa học xã hội",
        77 to "Xã hội", 78 to "Văn hoá", 79 to "Tài liệu", 80 to "Chuyện cá nhân", 81 to "Nhật ký",
        82 to "Triết học", 83 to "Địa danh", 84 to "Du lịch", 85 to "Các mối quan hệ", 86 to "Thể thao",
        87 to "Bóng chày", 88 to "Bóng rổ", 89 to "Cricket", 90 to "Thể thao ảo", 91 to "Bóng bầu dục",
        92 to "Golf", 93 to "Khúc côn cầu", 94 to "Rugby", 95 to "Chạy bộ", 96 to "Bóng đá",
        97 to "Bơi lội", 98 to "Quần vợt", 99 to "Bóng chuyền", 100 to "Thiên nhiên hoang dã",
        101 to "Đấu vật", 102 to "Công nghệ", 103 to "Hình sự có thật", 104 to "Truyền hình",
        105 to "Điện ảnh", 106 to "Bình luận sau chương trình", 107 to "Đánh giá", 108 to "Khí hậu",
        109 to "Thời tiết", 110 to "Board game", 111 to "Nhập vai", 112 to "Tiền mã hoá",
    )

    /** Shown first under "Phổ biến": what Vietnamese listeners pick most, in this order. */
    val POPULAR_IDS: List<Int> = listOf(
        55, 9, 25, 28, 41, 16, 20, 33, 29, 102, 53, 12, 85, 84, 6, 96, 23, 77,
    )

    fun vietnamese(id: Int, fallback: String): String = vi[id] ?: fallback
}
