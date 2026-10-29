// Port of coreApi/src/main/assets/blacklist.json, the Android app's offline
// fallback for its Remote Config blacklist (gambling spam feeds).

const FEED_IDS = new Set([
  "7101342", "7110542", "7108656", "7105470", "7105365", "7103988", "7103850",
  "7101028", "7101847", "7107835", "7106228", "7106310", "7102161", "7104152",
  "7101737", "7113285", "7113200", "7067706", "6960122",
]);

const KEYWORDS = [
  "bet", "keonhacai", "casino", "nhacai", "gamebai", "cado", "cacuoc",
  "cakhiatv", "cá cược", "nhà cái",
];

// The app matches with a plain `contains`, which also drops "Alphabet" or
// "Better Call Saul". Short ASCII keywords are matched as whole words here.
const KEYWORD_PATTERNS = KEYWORDS.map((k) =>
  /^[a-z]{1,4}$/.test(k)
    ? new RegExp(`(^|[^a-z0-9])${k}([^a-z0-9]|$)`, "i")
    : new RegExp(k.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i"),
);

export function isBlacklisted(item: {
  id?: number | string | null;
  feedId?: number | string | null;
  title?: string | null;
}): boolean {
  if (item.id != null && FEED_IDS.has(String(item.id))) return true;
  if (item.feedId != null && FEED_IDS.has(String(item.feedId))) return true;
  const title = item.title ?? "";
  return KEYWORD_PATTERNS.some((re) => re.test(title));
}
