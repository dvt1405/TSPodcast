#!/usr/bin/env python3
"""Frames raw iOS captures into App Store 6.9" screenshots (1320x2868), one set per locale.

    python3 -I compose_ios.py <raw-dir> <out-dir>

<raw-dir>/<app-language>/<NN-name>.png comes from iosApp/scripts/store-screenshots.sh
(StoreScreenshotsUITests). Captions: ios-captions.json. The status bar of the capture is masked. Rendering: headless Google Chrome with
the brand fonts from site/assets/fonts, same look as templates/appstore-6.9in-1320x2868.svg.
"""
import html, json, os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
FONTS = os.path.normpath(os.path.join(HERE, "../../../site/assets/fonts"))
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
W, H = 1320, 2868
# The device status bar (real clock, battery, "back to app" crumb) is hidden by stretching the
# clean top 1-px row of the capture (background only) over it. Heights are in the 986-px wide on-screen image.
STATUS_BAR_PX = 112  # 47 pt on a 3x 1284-px capture, scaled to 986 px, + a small margin
ROW_BELOW_PX = 1

PAGE = """<!doctype html><html><head><meta charset="utf-8"><style>
@font-face{{font-family:BVP;font-weight:500;src:url('file://{fonts}/be-vietnam-pro-400.woff2')}}
@font-face{{font-family:BVP;font-weight:800;src:url('file://{fonts}/be-vietnam-pro-800.woff2')}}
html,body{{margin:0;width:{w}px;height:{h}px;overflow:hidden}}
body{{background:linear-gradient(160deg,#3D7BFF 0%,#184BB4 55%,#0B1A4A 100%);font-family:BVP,'Hiragino Sans','Apple SD Gothic Neo','Kohinoor Devanagari',sans-serif;position:relative}}
.dot{{position:absolute;left:110px;top:110px;width:34px;height:34px;border-radius:50%;background:#FF7A59}}
h1{{position:absolute;left:110px;right:110px;top:180px;margin:0;color:#fff;font-weight:800;font-size:{fs}px;line-height:1.16;letter-spacing:-1.4px;white-space:pre-line}}
p{{position:absolute;left:110px;right:110px;top:{sub_top}px;margin:0;color:#C9D6FF;font-weight:500;font-size:49px;line-height:1.3}}
.phone{{position:absolute;left:145px;top:841px;width:1030px;height:2180px;border-radius:78px;background:#0A0F24;box-shadow:0 24px 72px rgba(0,0,0,.35)}}
.phone img{{position:absolute;left:22px;top:22px;width:986px;height:2136px;border-radius:65px;object-fit:cover;object-position:top}}
.sb{{position:absolute;left:22px;top:22px;width:986px;height:1px;border-radius:65px 65px 0 0;background:url('file://{shot}') 0 -{row}px/986px auto no-repeat;transform-origin:top;transform:scaleY({sbh})}}
</style></head><body><div class="dot"></div><h1>{title}</h1><p>{sub}</p>
<div class="phone"><img src="file://{shot}"><div class="sb"></div></div></body></html>"""


def render(html_text, out):
    with tempfile.NamedTemporaryFile("w", suffix=".html", delete=False, encoding="utf-8") as f:
        f.write(html_text)
        page = f.name
    profile = tempfile.mkdtemp(prefix="compose-chrome-")
    try:
        subprocess.run([CHROME, "--headless=new", f"--user-data-dir={profile}", "--no-first-run", "--disable-gpu", "--hide-scrollbars",
                        "--force-device-scale-factor=1", f"--window-size={W},{H}",
                        "--allow-file-access-from-files", "--virtual-time-budget=3000",
                        f"--screenshot={out}", f"file://{page}"],
                       check=True, capture_output=True)
    finally:
        os.unlink(page)
        shutil.rmtree(profile, ignore_errors=True)


def main(raw_dir, out_dir):
    spec = json.load(open(os.path.join(HERE, "ios-captions.json"), encoding="utf-8"))
    locales = spec["locales"]
    done = 0
    for asc, cfg in locales.items():
        captions = locales[cfg["same_as"]]["captions"] if "same_as" in cfg else cfg["captions"]
        for i, screen in enumerate(spec["screens"]):
            shot = os.path.join(raw_dir, cfg["app"], screen + ".png")
            if not os.path.exists(shot):
                print(f"skip {asc}/{screen}: no capture", file=sys.stderr)
                continue
            title, sub = captions[i]
            lines = title.count("\n") + 1
            fs = 100 if max(len(l) for l in title.split("\n")) <= 18 else 86
            sub_top = 180 + int(lines * fs * 1.16) + 40
            os.makedirs(os.path.join(out_dir, asc), exist_ok=True)
            render(PAGE.format(fonts=FONTS, w=W, h=H, fs=fs, sub_top=sub_top,
                               title=html.escape(title), sub=html.escape(sub), shot=os.path.abspath(shot),
                               row=ROW_BELOW_PX, sbh=STATUS_BAR_PX),
                   os.path.join(out_dir, asc, f"{screen}.png"))
            done += 1
    print(f"rendered {done} screenshots into {out_dir}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
