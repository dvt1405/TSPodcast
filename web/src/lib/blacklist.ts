// Gambling / betting spam filter for PodcastIndex results.
//
// Started as a port of coreApi/src/main/assets/blacklist.json (the Android app's offline fallback
// for its Remote Config blacklist). PodcastIndex keeps receiving Vietnamese casino/"game bài" spam
// under ever-new brand names and domains (go88, 68 Game Bài, jun88, kuwin, rikvip, b52club, 789bet,
// 123b…), so besides feed ids this matches brand names and gambling phrases in the title, author,
// owner and the feed/website host, with Vietnamese diacritics removed.

const FEED_IDS = new Set([
  "7101342", "7110542", "7108656", "7105470", "7105365", "7103988", "7103850",
  "7101028", "7101847", "7107835", "7106228", "7106310", "7102161", "7104152",
  "7101737", "7113285", "7113200", "7067706", "6960122",
]);

/** Brand names that only casinos use: matched anywhere, including inside domain names. */
const BRANDS = [
  "go88", "sunwin", "hitclub", "b52club", "789club", "789bet", "789win", "rikvip", "kuwin", "jun88",
  "new88", "hi88", "f8bet", "shbet", "8xbet", "fb88", "kubet", "mb66", "okvip", "33win", "nohu88",
  "sv388", "v9bet", "vn88", "8kbet", "ae888", "debet", "man88", "five88", "bong88", "188bet",
  "typhu88", "tf88", "yo88", "gemwin", "may88", "98win", "ok365", "abc8", "qh88", "vin777",
  "68gamebai", "68gb", "gamebai", "keonhacai", "nhacai", "cacuoc", "cakhiatv", "taixiu", "xocdia",
  "nohu", "soikeo", "doithuong", "dafabet", "12bet", "m88", "w88", "zbet", "bk8", "123bet", "123b", "b52",
];
/**
 * Short or ambiguous brands ("Interview 123b", "B-52 bomber"): only at the very start of a title or
 * name ("123B - Link…", "NOHU CAT") or as a whole label of a host name (123b.community).
 */
const WORD_ONLY = new Set(["nohu", "m88", "w88", "zbet", "bk8", "123b", "b52", "abc8", "12bet"]);

/** Gambling phrases (diacritics removed), matched as words in titles and names. */
const PHRASES = [
  "game bai", "doi thuong", "tai xiu", "no hu", "xoc dia", "lo de", "danh de", "soi keo", "keo nha cai",
  "nha cai", "ca cuoc", "ca do", "casino", "baccarat", "slot game", "ban ca doi thuong", "cong game",
  "link vao", "link trang chu", "dang ky nhan", "khuyen mai nap", "bet", "cado",
];

/** Lowercase, Vietnamese diacritics removed (đ → d). */
function fold(text: string): string {
  return text.normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/đ/gi, "d").toLowerCase();
}

const escape = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const word = (s: string) => new RegExp(`(^|[^a-z0-9])${escape(s).replace(/ /g, "[\\s._-]+")}([^a-z0-9]|$)`);
const BRAND_ANYWHERE = BRANDS.filter((b) => !WORD_ONLY.has(b));
const BRAND_AT_START = BRANDS.filter((b) => WORD_ONLY.has(b)).map((b) => new RegExp(`^\\s*${escape(b)}([^a-z0-9]|$)`));
const PHRASE_WORDS = PHRASES.map(word);

function host(url: string | null | undefined): string {
  if (!url) return "";
  try {
    return new URL(url).hostname.toLowerCase();
  } catch {
    return "";
  }
}

/** Whether a piece of text (title, author, owner name) looks like casino spam. */
export function looksLikeGambling(text: string | null | undefined): boolean {
  if (!text) return false;
  const folded = fold(text);
  const compact = folded.replace(/[^a-z0-9]/g, "");
  return (
    BRAND_ANYWHERE.some((b) => compact.includes(b)) ||
    BRAND_AT_START.some((re) => re.test(folded)) ||
    PHRASE_WORDS.some((re) => re.test(folded))
  );
}

/** Whether a host name (feed URL, website) belongs to a casino brand. */
function hostLooksLikeGambling(name: string): boolean {
  if (!name) return false;
  const labels = name.split(".");
  const compact = name.replace(/[^a-z0-9]/g, "");
  return BRAND_ANYWHERE.some((b) => compact.includes(b)) || labels.some((l) => WORD_ONLY.has(l));
}

export function isBlacklisted(item: {
  id?: number | string | null;
  feedId?: number | string | null;
  title?: string | null;
  author?: string | null;
  ownerName?: string | null;
  feedTitle?: string | null;
  feedUrl?: string | null;
  link?: string | null;
}): boolean {
  if (item.id != null && FEED_IDS.has(String(item.id))) return true;
  if (item.feedId != null && FEED_IDS.has(String(item.feedId))) return true;
  if ([item.title, item.author, item.ownerName, item.feedTitle].some(looksLikeGambling)) return true;
  return [host(item.feedUrl), host(item.link)].some(hostLooksLikeGambling);
}
