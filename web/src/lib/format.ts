import { locale } from "./i18n";

const intlLocale = locale === "vi" ? "vi-VN" : "en-US";

/** 3725 -> "1:02:05", 125 -> "2:05". */
export function formatClock(totalSeconds: number): string {
  if (!Number.isFinite(totalSeconds) || totalSeconds < 0) return "--:--";
  const s = Math.floor(totalSeconds);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  const mm = h > 0 ? String(m).padStart(2, "0") : String(m);
  return `${h > 0 ? `${h}:` : ""}${mm}:${String(sec).padStart(2, "0")}`;
}

/** 3725 -> "1 giờ 2 phút" / "1 h 2 min". */
export function formatDuration(totalSeconds: number | null | undefined): string {
  if (!totalSeconds || totalSeconds <= 0) return "";
  const h = Math.floor(totalSeconds / 3600);
  const m = Math.round((totalSeconds % 3600) / 60);
  if (locale === "vi") {
    if (h > 0) return m > 0 ? `${h} giờ ${m} phút` : `${h} giờ`;
    return `${Math.max(m, 1)} phút`;
  }
  if (h > 0) return m > 0 ? `${h} h ${m} min` : `${h} h`;
  return `${Math.max(m, 1)} min`;
}

/** ISO-8601 duration for schema.org, e.g. PT1H2M5S. */
export function isoDuration(totalSeconds: number | null | undefined): string | undefined {
  if (!totalSeconds || totalSeconds <= 0) return undefined;
  const s = Math.floor(totalSeconds);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  return `PT${h ? `${h}H` : ""}${m ? `${m}M` : ""}${sec || (!h && !m) ? `${sec}S` : ""}`;
}

/** Unix seconds -> localized date. Uses a fixed time zone so SSR and CSR agree. */
export function formatDate(unixSeconds: number | null | undefined): string {
  if (!unixSeconds) return "";
  return new Intl.DateTimeFormat(intlLocale, {
    day: "numeric",
    month: "short",
    year: "numeric",
    timeZone: "Asia/Ho_Chi_Minh",
  }).format(new Date(unixSeconds * 1000));
}

export function isoDate(unixSeconds: number | null | undefined): string | undefined {
  if (!unixSeconds) return undefined;
  return new Date(unixSeconds * 1000).toISOString();
}

export function truncate(text: string, max: number): string {
  if (text.length <= max) return text;
  const cut = text.slice(0, max);
  const lastSpace = cut.lastIndexOf(" ");
  return `${(lastSpace > max * 0.6 ? cut.slice(0, lastSpace) : cut).trimEnd()}…`;
}
