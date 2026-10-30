#!/bin/sh
# Sinh fixture audio cho test phát nhạc (M3 §4.6): tone 440 Hz, 60 giây, mono, AAC trong .m4a.
# Dùng: iosApp/scripts/make-test-audio.sh [đường dẫn ra]
# Mặc định ghi vào iosApp/TSPodcastUITests/Fixtures/tone-60s.m4a (file đã commit; chỉ chạy lại khi cần đổi).
# Cần python3 và afconvert (có sẵn trên macOS). Fixture chỉ thuộc các target test, không vào TSPodcast.app.
set -eu

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
OUTPUT=${1:-"$SCRIPT_DIR/../TSPodcastUITests/Fixtures/tone-60s.m4a"}
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

python3 -I - "$WORK/tone.wav" <<'PY'
import math, struct, sys, wave

rate, seconds, freq = 22050, 60, 440.0
with wave.open(sys.argv[1], "wb") as out:
    out.setnchannels(1)
    out.setsampwidth(2)
    out.setframerate(rate)
    frames = bytearray()
    for n in range(rate * seconds):
        frames += struct.pack("<h", int(0.2 * 32767 * math.sin(2 * math.pi * freq * n / rate)))
    out.writeframes(bytes(frames))
PY

mkdir -p "$(dirname "$OUTPUT")"
afconvert -f m4af -d aac -b 32000 "$WORK/tone.wav" "$OUTPUT"
echo "wrote $OUTPUT"
