#!/usr/bin/env python3
"""Checks Google Play and App Store listing files for length limits and banned words.

Usage (from the repo root):  python fastlane/check_store_metadata.py
Exits with status 1 if any check fails. Play limits are counted in characters (code
points); App Store keywords in UTF-8 bytes, as App Store Connect counts them.
"""
import glob
import os
import re
import sys
import unicodedata

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PLAY = os.path.join(ROOT, "fastlane", "metadata", "android")
IOS = os.path.join(ROOT, "iosApp", "fastlane", "metadata")
WHATSNEW = os.path.join(ROOT, "distribution", "whatsnew")

PLAY_LIMITS = {"title.txt": 30, "short_description.txt": 80, "full_description.txt": 4000}
CHANGELOG_LIMIT = 500
IOS_LIMITS = {"name.txt": 30, "subtitle.txt": 30, "description.txt": 4000,
              "promotional_text.txt": 170, "release_notes.txt": 4000}
IOS_KEYWORDS_BYTES = 100
PLAY_REQUIRED = ["title.txt", "short_description.txt", "full_description.txt"]
IOS_REQUIRED = list(IOS_LIMITS) + ["keywords.txt", "support_url.txt", "privacy_url.txt"]

# Advertising: the owner does not want ads mentioned anywhere in the listings.
ADS = [r"\bads?\b", r"advert", r"ad-supported", r"admob", r"quảng cáo", r"werbung", r"werbefrei",
       r"publicit", r"\bpub\b", r"anuncio", r"anúncio", r"publicidade", r"pubblicità", r"reklam",
       r"advertentie", r"reclame", r"\bannons", r"広告", r"광고", r"\biklan", r"विज्ञापन"]
# Superlatives / ranking claims.
RANKING = [r"\bbest\b", r"#\s*1\b", r"\bno\.?\s*1\b", r"number one", r"\btop\b", r"\bleading\b",
           r"\bnhất\b", r"số 1", r"số một", r"hàng đầu", r"\bmejor(es)?\b", r"\bmelhor(es)?\b",
           r"\bmeilleur", r"\bbeste[nrs]?\b", r"\bmigliore?\b", r"\bbästa\b", r"\bnajlep",
           r"\ben iyi\b", r"\bterbaik\b", r"\blíder", r"\bleader\b", r"führend", r"最高", r"一番",
           r"ナンバーワン", r"최고", r"1위", r"सबसे अच्छा", r"सर्वश्रेष्ठ", r"नंबर 1"]
# Other apps / brands, and features that are not live yet.
OTHER = [r"spotify", r"apple podcasts", r"google podcasts", r"youtube", r"castbox", r"pocket casts",
         r"overcast", r"thời sự", r"bản tin"]
FREE_IN_TITLE = [r"\bfree\b", r"miễn phí", r"gratis", r"gratuit", r"kostenlos", r"ücretsiz", r"無料",
                 r"무료", r"मुफ़्त", r"bezpłatn", r"darmowy"]
VOV_VOH = [r"\bVOV\b", r"\bVOH\b"]

errors, rows = [], []
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read().strip()


def scan(path, text, title_like=False, allow_station_names=False):
    low = text.lower()
    if re.search(r"/(it|it-IT)/", path):
        # Italian "ad" is a preposition ("ad ascoltare"); only flag the plural there.
        low = re.sub(r"ad", "", low)
    for pats, label in ((ADS, "advertising"), (RANKING, "ranking/superlative"), (OTHER, "other brand/unreleased")):
        for p in pats:
            if re.search(p, low):
                errors.append(f"{path}: {label} word /{p}/")
    if not allow_station_names:
        for p in VOV_VOH:
            if re.search(p, text):
                errors.append(f"{path}: station name /{p}/ only allowed in the vi Play description")
    if title_like:
        for p in FREE_IN_TITLE:
            if re.search(p, low):
                errors.append(f"{path}: price/promotion word /{p}/ in a title field")
    for ch in text:
        if unicodedata.category(ch) == "So" and ord(ch) > 0x2000 and ch not in "•×→":
            errors.append(f"{path}: emoji/symbol {ascii(ch)}")
            break
    for word in re.findall(r"[A-ZÀ-Ý]{4,}", text):
        errors.append(f"{path}: ALL CAPS word {word!r}")


def rel(p):
    return os.path.relpath(p, ROOT).replace("\\", "/")


# Google Play
play_locales = sorted(d for d in os.listdir(PLAY) if os.path.isdir(os.path.join(PLAY, d)))
for loc in play_locales:
    d = os.path.join(PLAY, loc)
    counts = {}
    for name in PLAY_REQUIRED:
        p = os.path.join(d, name)
        if not os.path.exists(p):
            errors.append(f"{rel(p)}: missing")
            continue
        t = read(p)
        counts[name] = len(t)
        if len(t) > PLAY_LIMITS[name]:
            errors.append(f"{rel(p)}: {len(t)} > {PLAY_LIMITS[name]} chars")
        scan(rel(p), t, title_like=name != "full_description.txt",
             allow_station_names=(loc == "vi" and name == "full_description.txt"))
    logs = sorted(glob.glob(os.path.join(d, "changelogs", "*.txt")))
    latest = logs[-1] if logs else None
    if latest:
        t = read(latest)
        counts["changelog"] = len(t)
        if len(t) > CHANGELOG_LIMIT:
            errors.append(f"{rel(latest)}: {len(t)} > {CHANGELOG_LIMIT} chars")
        scan(rel(latest), t)
    shots = glob.glob(os.path.join(d, "images", "phoneScreenshots", "*.png"))
    if len(shots) < 2:
        errors.append(f"{rel(d)}: needs at least 2 phone screenshots (has {len(shots)})")
    rows.append(("play", loc, counts, os.path.basename(latest) if latest else "-"))

# CI "What's new" files (r0adkll/upload-google-play: whatsnew-<Play locale>)
for p in sorted(glob.glob(os.path.join(WHATSNEW, "whatsnew-*"))):
    loc = os.path.basename(p)[len("whatsnew-"):]
    t = read(p)
    if loc not in play_locales:
        errors.append(f"{rel(p)}: no matching Play listing folder {loc}")
    if len(t) > CHANGELOG_LIMIT:
        errors.append(f"{rel(p)}: {len(t)} > {CHANGELOG_LIMIT} chars")
    scan(rel(p), t)
for loc in play_locales:
    if not os.path.exists(os.path.join(WHATSNEW, "whatsnew-" + loc)):
        errors.append(f"distribution/whatsnew/whatsnew-{loc}: missing")

# App Store
if os.path.isdir(IOS):
    for loc in sorted(d for d in os.listdir(IOS) if os.path.isdir(os.path.join(IOS, d))):
        d = os.path.join(IOS, loc)
        counts = {}
        for name in IOS_REQUIRED:
            p = os.path.join(d, name)
            if not os.path.exists(p):
                errors.append(f"{rel(p)}: missing")
                continue
            t = read(p)
            if name in IOS_LIMITS:
                counts[name] = len(t)
                if len(t) > IOS_LIMITS[name]:
                    errors.append(f"{rel(p)}: {len(t)} > {IOS_LIMITS[name]} chars")
            if name == "keywords.txt":
                b = len(t.encode("utf-8"))
                counts[name] = b
                if b > IOS_KEYWORDS_BYTES:
                    errors.append(f"{rel(p)}: {b} > {IOS_KEYWORDS_BYTES} bytes")
                if ", " in t or " ," in t:
                    errors.append(f"{rel(p)}: space next to a comma")
                kws = t.split(",")
                if len(kws) != len(set(k.lower() for k in kws)):
                    errors.append(f"{rel(p)}: duplicate keyword")
            if name.endswith("_url.txt"):
                if not t.startswith("https://"):
                    errors.append(f"{rel(p)}: not an https URL")
                continue
            scan(rel(p), t, title_like=name in ("name.txt", "subtitle.txt", "keywords.txt"))
            if re.search(r"</?[a-z]+>", t):
                errors.append(f"{rel(p)}: HTML tag (App Store shows plain text)")
            if re.search(r"\bandroid\b|google play|radio|rádio|radyo|ラジオ|라디오|रेडियो", t.lower()):
                errors.append(f"{rel(p)}: mentions Android/Google Play or radio (not in iOS v1)")
        rows.append(("ios", loc, counts, ""))

w = max(len(r[1]) for r in rows) if rows else 6
print("Google Play (characters): title/30 short/80 full/4000 changelog/500")
for kind, loc, c, log in rows:
    if kind == "play":
        print(f"  {loc:<{w}}  {c.get('title.txt', '-'):>3} {c.get('short_description.txt', '-'):>3} "
              f"{c.get('full_description.txt', '-'):>5} {c.get('changelog', '-'):>4}  ({log})")
print("App Store: name/30 subtitle/30 description/4000 keywords/100 bytes promo/170 release notes")
for kind, loc, c, _ in rows:
    if kind == "ios":
        print(f"  {loc:<{w}}  {c.get('name.txt', '-'):>3} {c.get('subtitle.txt', '-'):>3} "
              f"{c.get('description.txt', '-'):>5} {c.get('keywords.txt', '-'):>4}B "
              f"{c.get('promotional_text.txt', '-'):>4} {c.get('release_notes.txt', '-'):>4}")

if errors:
    print(f"\nFAILED ({len(errors)}):")
    for e in errors:
        print("  " + e)
    sys.exit(1)
print("\nOK: all limits respected, no banned words.")
