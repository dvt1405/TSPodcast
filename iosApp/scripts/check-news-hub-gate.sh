#!/bin/bash
# Cổng phát hành của "Thời sự & Bản tin" (M6 §3.4, §7 mục 1; app-store-submission-plan §15/§16).
#
#   iosApp/scripts/check-news-hub-gate.sh [đường dẫn JSON cấu hình hub]
#
# Mặc định đọc shared/assets/news_hub_default.json (file đóng gói). Nếu Remote Config `news_hub_v1`
# đang có giá trị thì export giá trị đó ra file rồi truyền đường dẫn vào (Remote Config thay hẳn file
# đóng gói). Đếm nguồn có `confirmedAt` là ngày `YYYY-MM-DD` (đúng luật của NewsHubConfigRepository;
# Release chỉ hiện các nguồn này) theo từng section, in:
#
#   confirmed news=<n> night=<m>
#
# Đạt (exit 0) khi `enabled` khác false VÀ (≥ 2 nguồn tin + ≥ 1 nguồn đêm khuya, HOẶC ≥ 3 nguồn tin) —
# ngưỡng owner chọn. Dưới ngưỡng, hub tắt hoặc JSON hỏng → exit 1: owner quyết; nếu bỏ hub thì gỡ hub
# khỏi binary trước khi archive (không ẩn). Không gọi mạng, không đọc secret.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CONFIG="${1:-$ROOT/shared/assets/news_hub_default.json}"

if [[ ! -f "$CONFIG" ]]; then
  echo "không thấy file cấu hình: $CONFIG" >&2
  exit 2
fi

exec /usr/bin/python3 -I - "$CONFIG" <<'PY'
import json
import re
import sys

ISO_DATE = re.compile(r"^\d{4}-\d{2}-\d{2}$")

path = sys.argv[1]
try:
    with open(path, encoding="utf-8") as handle:
        config = json.load(handle)
except (OSError, ValueError) as error:
    print(f"JSON hỏng: {type(error).__name__}")
    sys.exit(1)

def confirmed(section_id):
    count = 0
    for section in config.get("sections") or []:
        if section.get("id") != section_id:
            continue
        for source in section.get("sources") or []:
            value = source.get("confirmedAt")
            if isinstance(value, str) and ISO_DATE.match(value.strip()):
                count += 1
    return count

news = confirmed("news")
night = confirmed("night")
print(f"confirmed news={news} night={night}")

if config.get("enabled", True) is False:
    print("FAIL: hub đang tắt trong cấu hình (enabled=false). Bỏ hub thì gỡ khỏi binary, không ẩn.")
    sys.exit(1)
if (news >= 2 and night >= 1) or news >= 3:
    print("PASS: đủ ngưỡng (≥ 2 nguồn tin + kệ đêm, hoặc ≥ 3 nguồn tin).")
    sys.exit(0)
print("FAIL: dưới ngưỡng (cần ≥ 2 nguồn tin + kệ đêm, hoặc ≥ 3 nguồn tin có confirmedAt). Owner quyết trước khi archive.")
sys.exit(1)
PY
