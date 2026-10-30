#!/bin/bash
# Kiểm "required reason API" của Apple trên một bản .app đã build (M4-AC22, app-store-submission-plan §5).
#
#   iosApp/scripts/check-required-reason-apis.sh <đường dẫn TSPodcast.app (Release)>
#
# Quét binary chính của app, mọi appex trong PlugIns/ và mọi framework trong Frameworks/ (framework
# rỗng/stub thì bỏ qua) bằng `nm -u` (symbol C/ObjC/hằng Foundation import vào) và `strings` (selector ObjC),
# tìm 5 nhóm: FileTimestamp, SystemBootTime, DiskSpace, ActiveKeyboards, UserDefaults. Framework Kotlin
# `Shared` và Firebase được link static nên nằm trong binary chính của app.
#
# Với mỗi bundle, so với `PrivacyInfo.xcprivacy` ở gốc bundle:
#   - nhóm tìm thấy mà manifest không khai          → lỗi;
#   - manifest khai nhóm không tìm thấy (thừa)      → lỗi;
#   - reason rỗng hoặc không thuộc danh sách Apple  → lỗi;
#   - bundle không dùng nhóm nào thì không cần manifest.
# In bảng bundle → nhóm → symbol làm bằng chứng → reason, rồi thoát 0 (đạt) hoặc 1 (có lỗi).
#
# Đây là kiểm tĩnh: Apple vẫn có thể báo ITMS-91053 cho API gọi gián tiếp. Sau mỗi lần upload TestFlight
# đối chiếu email của Apple và Xcode › Organizer › Generate Privacy Report (handoff).
set -euo pipefail

if [[ $# -ne 1 || ! -d "$1" ]]; then
  echo "usage: $0 <path/to/App.app>" >&2
  exit 2
fi

exec /usr/bin/python3 -I - "$1" <<'PY'
import os
import plistlib
import subprocess
import sys

APP = os.path.abspath(sys.argv[1])

# Apple: https://developer.apple.com/documentation/bundleresources/describing-use-of-required-reason-api
CATEGORIES = {
    "NSPrivacyAccessedAPICategoryFileTimestamp": {
        "symbols": {"_stat", "_fstat", "_fstatat", "_lstat", "_getattrlist", "_fgetattrlist",
                    "_getattrlistat", "_getattrlistbulk", "_NSFileCreationDate", "_NSFileModificationDate",
                    "_NSURLContentModificationDateKey", "_NSURLCreationDateKey",
                    "_NSURLAttributeModificationDateKey"},
        "selectors": {"fileModificationDate"},
        "reasons": {"DDA9.1", "C617.1", "3B52.1", "0A2A.1"},
    },
    "NSPrivacyAccessedAPICategorySystemBootTime": {
        "symbols": {"_mach_absolute_time"},
        "selectors": {"systemUptime"},
        "reasons": {"35F9.1", "8FFB.1", "3D61.1"},
    },
    "NSPrivacyAccessedAPICategoryDiskSpace": {
        "symbols": {"_statfs", "_statvfs", "_fstatfs", "_fstatvfs", "_NSFileSystemFreeSize",
                    "_NSFileSystemSize", "_NSURLVolumeAvailableCapacityKey",
                    "_NSURLVolumeAvailableCapacityForImportantUsageKey",
                    "_NSURLVolumeAvailableCapacityForOpportunisticUsageKey", "_NSURLVolumeTotalCapacityKey"},
        "selectors": set(),
        "reasons": {"85F4.1", "E174.1", "7D9E.1", "B728.1"},
    },
    "NSPrivacyAccessedAPICategoryActiveKeyboards": {
        "symbols": set(),
        "selectors": {"activeInputModes"},
        "reasons": {"3EC4.1", "54BD.1"},
    },
    "NSPrivacyAccessedAPICategoryUserDefaults": {
        "symbols": {"_OBJC_CLASS_$_NSUserDefaults"},
        "selectors": set(),
        "reasons": {"CA92.1", "1C8F.1", "C56D.1", "AC6B.1"},
    },
}


def run(*args):
    return subprocess.run(args, capture_output=True, text=True, errors="replace").stdout


def bundles():
    """(label, bundle dir, executable) for the app, its extensions and its frameworks."""
    found = []

    def executable(bundle):
        info_path = os.path.join(bundle, "Info.plist")
        with open(info_path, "rb") as f:
            name = plistlib.load(f).get("CFBundleExecutable")
        return os.path.join(bundle, name) if name else None

    found.append((os.path.basename(APP), APP, executable(APP)))
    for folder in ("PlugIns", "Frameworks"):
        root = os.path.join(APP, folder)
        if not os.path.isdir(root):
            continue
        for entry in sorted(os.listdir(root)):
            bundle = os.path.join(root, entry)
            if os.path.isfile(os.path.join(bundle, "Info.plist")):
                found.append((f"{folder}/{entry}", bundle, executable(bundle)))
    return found


def scan(binary):
    """Category → sorted evidence (imported symbols, ObjC selector strings)."""
    imported = set(run("nm", "-u", binary).split())
    strings = set(run("strings", "-a", binary).splitlines())
    result = {}
    for category, spec in CATEGORIES.items():
        evidence = sorted((spec["symbols"] & imported) | (spec["selectors"] & strings))
        if evidence:
            result[category] = evidence
    return result


def declared(bundle):
    """Category → reasons from the bundle's PrivacyInfo.xcprivacy, or None when there is none."""
    path = os.path.join(bundle, "PrivacyInfo.xcprivacy")
    if not os.path.isfile(path):
        return None
    with open(path, "rb") as f:
        manifest = plistlib.load(f)
    return {
        item.get("NSPrivacyAccessedAPIType", "?"): list(item.get("NSPrivacyAccessedAPITypeReasons", []))
        for item in manifest.get("NSPrivacyAccessedAPITypes", [])
    }


def short(category):
    return category.replace("NSPrivacyAccessedAPICategory", "")


errors = []
rows = []
for label, bundle, binary in bundles():
    if not binary or not os.path.isfile(binary):
        errors.append(f"{label}: executable not found")
        continue
    if "no symbols" in subprocess.run(["nm", "-u", binary], capture_output=True, text=True).stderr:
        rows.append((label, "-", "(stub, no symbols)", "-"))
        continue
    found = scan(binary)
    reasons = declared(bundle)
    if not found and reasons is None:
        rows.append((label, "-", "(no required-reason API)", "no manifest needed"))
        continue
    reasons = reasons or {}
    for category, evidence in found.items():
        given = reasons.get(category)
        if given is None:
            errors.append(f"{label}: {short(category)} used ({', '.join(evidence)}) but not declared")
            given_text = "MISSING"
        else:
            bad = [r for r in given if r not in CATEGORIES[category]["reasons"]]
            if not given:
                errors.append(f"{label}: {short(category)} declared without a reason")
            if bad:
                errors.append(f"{label}: {short(category)} has unapproved reason(s) {', '.join(bad)}")
            given_text = ", ".join(given) or "EMPTY"
        rows.append((label, short(category), ", ".join(evidence), given_text))
    for category, given in reasons.items():
        if category not in CATEGORIES:
            errors.append(f"{label}: unknown category {category}")
        elif category not in found:
            errors.append(f"{label}: {short(category)} declared ({', '.join(given)}) but not found in the binary")
            rows.append((label, short(category), "NOT FOUND", ", ".join(given)))

width = [max(len(r[i]) for r in rows + [("Bundle", "Category", "Evidence", "Reasons")]) for i in range(4)]
header = ("Bundle", "Category", "Evidence", "Reasons")
for row in [header] + rows:
    print("  ".join(cell.ljust(width[i]) for i, cell in enumerate(row)).rstrip())

if errors:
    print()
    for error in errors:
        print(f"ERROR {error}")
    print("REQUIRED REASON CHECK FAIL")
    sys.exit(1)
print()
print("REQUIRED REASON CHECK PASS")
PY
