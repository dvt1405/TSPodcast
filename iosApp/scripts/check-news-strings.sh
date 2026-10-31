#!/bin/bash
# Kiểm tĩnh M6-AC18: mọi khoá `news.*` của Localizable.xcstrings (vi + en, cả dạng số nhiều) không chứa
# Radio / Đài / Phát thanh / Trực tiếp / Live / Kênh (không phân biệt hoa thường, theo ranh giới từ).
# Tiêu đề feed/tập là chữ của nhà phát hành, không nằm trong catalog nên không bị kiểm ở đây.
#
#   iosApp/scripts/check-news-strings.sh
#
# In số khoá `news.*`, số vi phạm, số khoá thiếu vi/en; exit 0 khi không vi phạm, không thiếu và có ≥ 1 khoá.
# Bản trên máy: NewsHubStringsTests (đọc bảng đã biên dịch trong bundle).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

exec /usr/bin/python3 -I - "$ROOT/iosApp/TSPodcast/Resources/Localizable.xcstrings" <<'PY'
import json
import re
import sys

FORBIDDEN = re.compile(r"\b(radio|đài|phát thanh|trực tiếp|live|kênh)\b", re.IGNORECASE)

with open(sys.argv[1], encoding="utf-8") as handle:
    catalog = json.load(handle)

def values(node):
    if isinstance(node, dict):
        unit = node.get("stringUnit")
        if isinstance(unit, dict) and isinstance(unit.get("value"), str):
            yield unit["value"]
        for key, child in node.items():
            if key != "stringUnit":
                yield from values(child)

keys = sorted(k for k in catalog["strings"] if k.startswith("news."))
violations = []
missing = []
for key in keys:
    localizations = catalog["strings"][key].get("localizations", {})
    for language in ("vi", "en"):
        texts = list(values(localizations.get(language, {})))
        if not texts:
            missing.append(f"{language} {key}")
        for text in texts:
            if FORBIDDEN.search(text):
                violations.append(f"{language} {key}: {text}")

print(f"news_keys={len(keys)} violations={len(violations)} missing={len(missing)}")
for line in violations + missing:
    print("  " + line)
sys.exit(0 if keys and not violations and not missing else 1)
PY
