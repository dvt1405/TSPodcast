import "server-only";
import { htmlToText } from "./text";
import type { Transcript } from "./types";

// podcast:transcript formats: text/vtt, application/x-subrip (srt),
// application/json (podcast namespace JSON), text/html, text/plain.
const MAX_BYTES = 2_000_000;
const TIMEOUT_MS = 10_000;

function rank(t: Transcript): number {
  const type = t.type.toLowerCase();
  if (type.includes("json")) return 0;
  if (type.includes("vtt")) return 1;
  if (type.includes("srt") || type.includes("subrip")) return 2;
  if (type.includes("plain")) return 3;
  if (type.includes("html")) return 4;
  return 5;
}

function cuesToText(raw: string): string {
  return raw
    .replace(/\r/g, "")
    .split("\n")
    .filter(
      (line) =>
        line.trim() !== "" &&
        !/^WEBVTT/.test(line) &&
        !/^NOTE\b/.test(line) &&
        !/^\d+$/.test(line.trim()) &&
        !/-->/.test(line),
    )
    .map((line) => line.replace(/<[^>]+>/g, "").trim())
    .join(" ")
    .replace(/\s+/g, " ")
    .trim();
}

function jsonToText(raw: string): string {
  const data = JSON.parse(raw) as { segments?: { speaker?: string; body?: string }[] };
  const out: string[] = [];
  let lastSpeaker = "";
  for (const seg of data.segments ?? []) {
    const body = (seg.body ?? "").trim();
    if (!body) continue;
    if (seg.speaker && seg.speaker !== lastSpeaker) {
      out.push(`\n${seg.speaker}: ${body}`);
      lastSpeaker = seg.speaker;
    } else {
      out.push(body);
    }
  }
  return out.join(" ").replace(/[ \t]+/g, " ").trim();
}

/**
 * Transcript URLs come from third-party feeds, so the server only fetches
 * public https hosts by name: no IP literals, localhost or internal suffixes.
 */
function isPublicHttpsUrl(value: string): boolean {
  try {
    const u = new URL(value);
    if (u.protocol !== "https:" || u.port) return false;
    const host = u.hostname.toLowerCase();
    if (!host.includes(".")) return false;
    if (/^[\d.]+$/.test(host) || host.startsWith("[")) return false;
    return !/(^localhost$|\.localhost$|\.local$|\.internal$|\.lan$|\.home$|\.corp$)/.test(host);
  } catch {
    return false;
  }
}

/** Follows up to 3 redirects by hand so each hop is re-checked. */
async function fetchPublic(url: string): Promise<Response | null> {
  let current = url;
  for (let hop = 0; hop < 4; hop++) {
    if (!isPublicHttpsUrl(current)) return null;
    const res = await fetch(current, {
      cache: "no-store",
      redirect: "manual",
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    if (res.status >= 300 && res.status < 400) {
      const location = res.headers.get("location");
      if (!location) return null;
      current = new URL(location, current).toString();
      continue;
    }
    return res;
  }
  return null;
}

/** Fetches the best transcript the feed offers, as plain text. Null on any failure. */
export async function fetchTranscriptText(transcripts: Transcript[]): Promise<string | null> {
  const candidates = transcripts.filter((t) => isPublicHttpsUrl(t.url)).sort((a, b) => rank(a) - rank(b));
  for (const t of candidates.slice(0, 2)) {
    try {
      const res = await fetchPublic(t.url);
      if (!res?.ok) continue;
      const length = Number(res.headers.get("content-length") ?? 0);
      if (length > MAX_BYTES) continue;
      const raw = (await res.text()).slice(0, MAX_BYTES);
      const type = (t.type || res.headers.get("content-type") || "").toLowerCase();
      let text: string;
      if (type.includes("json")) text = jsonToText(raw);
      else if (type.includes("vtt") || type.includes("srt") || type.includes("subrip") || /-->/.test(raw.slice(0, 2000)))
        text = cuesToText(raw);
      else if (type.includes("html")) text = htmlToText(raw);
      else text = raw.trim();
      if (text.length > 200) return text;
    } catch {
      // Try the next candidate.
    }
  }
  return null;
}
