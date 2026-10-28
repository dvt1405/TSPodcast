import "server-only";
import * as cheerio from "cheerio";
import { unstable_cache } from "next/cache";
import type { RadioChannel } from "./types";

// Web port of coreRadio (RadioRepository = VOV, VOHRepository = VOH).
//
// VOV: the Android parser reads `.row .col` on vovmedia.vn and then scrapes a
// `url: "..."` regex per channel page. The current vovmedia.vn markup (checked
// 2026-10-29) has neither; channels are Alpine.js `x-data` blobs on
// /radio carrying title, artwork and an HLS `sources[].url`. Parsed here.
// VOH: same as VOHRepository.parseNextDataToDTO - `__NEXT_DATA__` on the
// "more channels" page lists `chanelList[]` with a direct HLS `url`.

const UA =
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
const TIMEOUT_MS = 12_000;

async function getHtml(url: string, referer?: string): Promise<string> {
  const res = await fetch(url, {
    headers: { "User-Agent": UA, ...(referer ? { Referer: referer } : {}) },
    cache: "no-store",
    redirect: "follow",
    signal: AbortSignal.timeout(TIMEOUT_MS),
  });
  if (!res.ok) throw new Error(`${url} responded ${res.status}`);
  return res.text();
}

const httpsOnly = (u: string) => (u.startsWith("https://") ? u : "");

export async function fetchVovChannels(): Promise<RadioChannel[]> {
  const raw = await getHtml("https://vovmedia.vn/radio");
  // The JSON sits inside x-data="{ item: JSON.parse('...') }" with "
  // quotes and \/ slashes, so decode those before matching.
  const html = raw
    .replace(/\\u0022/g, '"')
    .replace(/\\\\\\\//g, "/")
    .replace(/\\\//g, "/");
  const re =
    /"title":"([^"]+)","subtitle":"([^"]*)","label":"[^"]*","artwork":"([^"]*)","seed":"([^"]+)","isLive":true,"sources":\[\{"url":"([^"]+)"/g;
  const seen = new Set<string>();
  const channels: RadioChannel[] = [];
  for (const m of html.matchAll(re)) {
    const [, title, subtitle, artwork, seed, stream] = m;
    if (seen.has(seed) || !httpsOnly(stream)) continue;
    seen.add(seed);
    channels.push({
      id: `vov-${seed}`,
      name: title,
      subtitle,
      logo: httpsOnly(artwork),
      streamUrl: stream,
      source: "VOV",
      pageUrl: `https://vovmedia.vn/radio/${seed}`,
    });
  }
  return channels;
}

interface VohChannel {
  id?: string;
  title?: string;
  titleAcronym?: string;
  slug?: string;
  url?: string;
  logoPath?: string;
  location?: string;
}

export async function fetchVohChannels(): Promise<RadioChannel[]> {
  const base = "https://voh.com.vn/radio";
  const mainHtml = await getHtml(base);
  let $ = cheerio.load(mainHtml);
  const more = $("a.btn-more-radio").attr("href");
  if (more) {
    try {
      $ = cheerio.load(await getHtml(new URL(more, "https://voh.com.vn").toString(), base));
    } catch {
      // Fall back to the first page, as VOHRepository does.
    }
  }
  const nextData = $("script#__NEXT_DATA__").html();
  if (!nextData) return [];
  const json = JSON.parse(nextData) as {
    props?: { pageProps?: { pageData?: { blockList?: { id?: string; blockName?: string; chanelList?: VohChannel[] }[] } } };
  };
  const blocks = json.props?.pageProps?.pageData?.blockList ?? [];
  const block =
    blocks.find((b) => b.id === "223" || b.blockName === "Kênh") ??
    blocks.find((b) => Array.isArray(b.chanelList) && b.chanelList.length > 0);
  return (block?.chanelList ?? [])
    .filter((c) => c.title && c.url && httpsOnly(c.url))
    .map<RadioChannel>((c) => ({
      id: `voh-${c.id ?? c.title}`,
      name: c.title!,
      subtitle: [c.titleAcronym, c.location].filter(Boolean).join(" · "),
      logo: httpsOnly(c.logoPath ?? ""),
      streamUrl: c.url!,
      source: "VOH",
      pageUrl: c.slug ?? base,
    }));
}

async function settle(fn: () => Promise<RadioChannel[]>, label: string) {
  try {
    return { ok: true as const, channels: await fn() };
  } catch (error) {
    console.error(`[radio:${label}]`, error instanceof Error ? error.message : error);
    return { ok: false as const, channels: [] as RadioChannel[] };
  }
}

const loadRadio = async () => {
  const [vov, voh] = await Promise.all([
    settle(fetchVovChannels, "vov"),
    settle(fetchVohChannels, "voh"),
  ]);
  // Throwing keeps a total outage out of the 6-hour cache.
  if (!vov.ok && !voh.ok) throw new Error("all radio sources failed");
  return { vov, voh };
};

/** Both sources, cached for 6 hours. A failing source yields an empty list. */
export const getRadioChannels = unstable_cache(loadRadio, ["radio-channels"], {
  revalidate: 21_600,
  tags: ["radio"],
});
