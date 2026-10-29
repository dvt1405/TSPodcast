import "server-only";
import { createHash } from "node:crypto";
import { unstable_cache } from "next/cache";
import { isBlacklisted } from "./blacklist";
import type { Category, Episode, PodcastDetail, PodcastSummary, Transcript } from "./types";

// Mirrors the Android client (core/.../di/NetworkModule.kt + securedToken):
//   X-Auth-Key    = API key
//   X-Auth-Date   = unix seconds
//   Authorization = sha1(key + secret + date), lowercase hex
//   User-Agent    = identifies the app (the Android app sends TSSPodcast/Android)
// Keys come only from server env vars and never reach the browser bundle
// ("server-only" makes importing this file from a client component a build error).
const API_BASE = "https://api.podcastindex.org/api/1.0/";
const USER_AGENT = process.env.PODCASTINDEX_USER_AGENT?.trim() || "TSSPodcast/Web";
const REQUEST_TIMEOUT_MS = 10_000;

// Cache lifetimes (seconds). PodcastIndex terms 5.5(1) forbid keeping copies
// longer than the API allows; these are short, rolling caches, not an archive.
export const REVALIDATE = {
  trending: 900,
  recent: 600,
  detail: 3600,
  categories: 86_400,
} as const;

export class MissingApiKeysError extends Error {
  constructor() {
    super("PODCASTINDEX_API_KEY / PODCASTINDEX_API_SECRET are not set");
    this.name = "MissingApiKeysError";
  }
}

export class PodcastIndexError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = "PodcastIndexError";
  }
}

export function isPodcastIndexConfigured(): boolean {
  return Boolean(
    process.env.PODCASTINDEX_API_KEY?.trim() && process.env.PODCASTINDEX_API_SECRET?.trim(),
  );
}

type Params = Record<string, string | number | boolean | null | undefined>;

async function apiGet<T>(path: string, params: Params = {}): Promise<T> {
  const key = process.env.PODCASTINDEX_API_KEY?.trim();
  const secret = process.env.PODCASTINDEX_API_SECRET?.trim();
  if (!key || !secret) throw new MissingApiKeysError();

  const date = Math.floor(Date.now() / 1000).toString();
  const authorization = createHash("sha1").update(key + secret + date).digest("hex");

  const url = new URL(path, API_BASE);
  for (const [name, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === false || value === "") continue;
    url.searchParams.set(name, String(value));
  }

  const res = await fetch(url, {
    headers: {
      "X-Auth-Key": key,
      "X-Auth-Date": date,
      Authorization: authorization,
      "User-Agent": USER_AGENT,
      Accept: "application/json",
    },
    // Cached by unstable_cache below instead: the auth headers change every
    // second and are part of Next's fetch-cache key.
    cache: "no-store",
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  });
  if (!res.ok) {
    throw new PodcastIndexError(`PodcastIndex ${path} responded ${res.status}`, res.status);
  }
  return (await res.json()) as T;
}

/** unstable_cache wrapper: arguments are part of the key; errors are not cached. */
function cached<A extends unknown[], R>(
  name: string,
  revalidate: number,
  fn: (...args: A) => Promise<R>,
): (...args: A) => Promise<R> {
  return unstable_cache(fn, ["podcastindex", name], {
    revalidate,
    tags: ["podcastindex", `podcastindex:${name}`],
  });
}

// ---------- raw API shapes (only the fields we read) ----------

interface RawFeed {
  id?: number;
  title?: string;
  author?: string;
  ownerName?: string;
  image?: string;
  artwork?: string;
  description?: string;
  language?: string;
  categories?: Record<string, string> | null;
  url?: string;
  link?: string;
  trendScore?: number;
  newestItemPublishTime?: number;
  episodeCount?: number;
  explicit?: boolean | number;
  medium?: string;
  podcastGuid?: string;
  itunesId?: number | null;
  lastUpdateTime?: number;
  funding?: { url?: string; message?: string } | null;
  dead?: number;
}

interface RawEpisode {
  id?: number;
  title?: string;
  description?: string;
  link?: string;
  guid?: string;
  datePublished?: number;
  enclosureUrl?: string;
  enclosureType?: string;
  duration?: number | null;
  explicit?: number | boolean;
  episode?: number | null;
  season?: number | null;
  image?: string;
  feedImage?: string;
  feedId?: number;
  feedTitle?: string;
  feedLanguage?: string;
  transcriptUrl?: string;
  transcripts?: { url?: string; type?: string }[];
  chaptersUrl?: string;
}

// ---------- normalizers ----------

/**
 * List items only need a snippet. Some feeds ship megabytes of description,
 * and unstable_cache refuses entries over 2 MB, so lists are capped here.
 */
const LIST_DESCRIPTION_MAX = 1200;

/**
 * Only http(s) artwork URLs. Some music feeds inline megabytes of base64
 * `data:` artwork, which bloats every cached list and HTML page.
 */
function imageUrl(...candidates: (string | null | undefined)[]): string {
  return candidates.find((c): c is string => Boolean(c && /^https?:\/\//i.test(c) && c.length < 2048)) ?? "";
}

function toPodcastSummary(f: RawFeed, descriptionMax = LIST_DESCRIPTION_MAX): PodcastSummary {
  return {
    id: Number(f.id ?? 0),
    title: (f.title ?? "").trim(),
    author: (f.author || f.ownerName || "").trim(),
    image: imageUrl(f.artwork, f.image),
    description: (f.description ?? "").slice(0, descriptionMax),
    language: f.language ?? "",
    categories: f.categories ? Object.values(f.categories).filter(Boolean) : [],
    feedUrl: f.url,
    trendScore: f.trendScore,
    newestItemPublishTime: f.newestItemPublishTime,
  };
}

function toPodcastDetail(f: RawFeed): PodcastDetail {
  return {
    ...toPodcastSummary(f, 20_000),
    link: f.link ?? "",
    ownerName: f.ownerName ?? "",
    episodeCount: Number(f.episodeCount ?? 0),
    // Feed.explicit is a boolean here while Podcast.explicit was an int in the
    // Android models (the cause of the bymedium Gson crash in dddd818).
    explicit: f.explicit === true || f.explicit === 1,
    medium: f.medium ?? "podcast",
    podcastGuid: f.podcastGuid ?? "",
    itunesId: f.itunesId ?? null,
    lastUpdateTime: Number(f.lastUpdateTime ?? 0),
    funding: f.funding?.url ? { url: f.funding.url, message: f.funding.message ?? "" } : null,
  };
}

function toEpisode(e: RawEpisode): Episode {
  const transcripts: Transcript[] = (e.transcripts ?? [])
    .filter((t): t is { url: string; type?: string } => Boolean(t?.url))
    .map((t) => ({ url: t.url, type: t.type ?? "" }));
  if (transcripts.length === 0 && e.transcriptUrl) {
    transcripts.push({ url: e.transcriptUrl, type: "" });
  }
  return {
    id: Number(e.id ?? 0),
    title: (e.title ?? "").trim(),
    description: e.description ?? "",
    link: e.link ?? "",
    guid: e.guid ?? "",
    datePublished: Number(e.datePublished ?? 0),
    enclosureUrl: e.enclosureUrl ?? "",
    enclosureType: e.enclosureType ?? "",
    duration: Number(e.duration ?? 0),
    explicit: e.explicit === true || e.explicit === 1,
    episode: e.episode ?? null,
    season: e.season ?? null,
    image: imageUrl(e.image, e.feedImage),
    feedId: Number(e.feedId ?? 0),
    feedTitle: (e.feedTitle ?? "").trim(),
    feedImage: imageUrl(e.feedImage),
    feedLanguage: e.feedLanguage ?? "",
    transcripts,
    chaptersUrl: e.chaptersUrl ?? "",
  };
}

const keepPodcast = (p: PodcastSummary) => p.id > 0 && p.title !== "" && !isBlacklisted(p);
const keepEpisode = (e: Episode) =>
  e.id > 0 && e.enclosureUrl !== "" && !isBlacklisted({ id: e.id, feedId: e.feedId, title: e.title });

/** PodcastIndex returns `feed: []` / `episode: []` instead of 404 for unknown ids. */
function single<T>(value: T | T[] | undefined | null): T | null {
  if (!value) return null;
  if (Array.isArray(value)) return value[0] ?? null;
  return value;
}

// ---------- endpoints ----------

/** podcasts/trending over the last 7 days (the window MainViewModel uses). */
export const getTrending = cached(
  "trending",
  REVALIDATE.trending,
  async (opts: { max?: number; lang?: string; cat?: string } = {}) => {
    const since = Math.floor(Date.now() / 1000) - 7 * 24 * 3600;
    const data = await apiGet<{ feeds?: RawFeed[] }>("podcasts/trending", {
      max: opts.max ?? 40,
      since,
      lang: opts.lang,
      cat: opts.cat,
    });
    return (data.feeds ?? []).map((f) => toPodcastSummary(f)).filter(keepPodcast);
  },
);

/**
 * podcasts/bymedium?medium=music. The API has no ranking or genre filter for
 * music (see commit dddd818), so this is presented as a library, newest-updated
 * first. It also ignores `max` (checked 2026-10-29: ~8,600 feeds, several MB,
 * per call), so the list is cut down here before it is cached.
 */
export const getMusicFeeds = cached("music", REVALIDATE.trending, async (max: number = 40) => {
  const data = await apiGet<{ feeds?: RawFeed[] }>("podcasts/bymedium", {
    medium: "music",
    max,
  });
  return (data.feeds ?? [])
    .filter((f) => !f.dead)
    .sort((a, b) => Number(b.lastUpdateTime ?? 0) - Number(a.lastUpdateTime ?? 0))
    .slice(0, max * 2)
    .map((f) => toPodcastSummary(f))
    .filter(keepPodcast)
    .slice(0, max);
});

export const getCategories = cached("categories", REVALIDATE.categories, async () => {
  const data = await apiGet<{ feeds?: { id?: number; name?: string }[] }>("categories/list");
  return (data.feeds ?? [])
    .filter((c): c is { id: number; name: string } => Boolean(c.id && c.name))
    .map<Category>((c) => ({ id: c.id, name: c.name }));
});

export const getRecentEpisodes = cached("recent", REVALIDATE.recent, async (max: number = 20) => {
  // Over-fetch: the blacklist and missing enclosures drop some items.
  const data = await apiGet<{ items?: RawEpisode[] }>("recent/episodes", { max: max * 2 });
  return (data.items ?? [])
    .map(toEpisode)
    .filter(keepEpisode)
    .slice(0, max)
    .map((e) => ({ ...e, description: e.description.slice(0, LIST_DESCRIPTION_MAX) }));
});

export const getPodcast = cached("podcast", REVALIDATE.detail, async (id: number) => {
  const data = await apiGet<{ feed?: RawFeed | RawFeed[] }>("podcasts/byfeedid", { id });
  const feed = single(data.feed);
  if (!feed?.id) return null;
  const podcast = toPodcastDetail(feed);
  return isBlacklisted(podcast) ? null : podcast;
});

export const getEpisodesByFeed = cached(
  "episodes-by-feed",
  REVALIDATE.detail,
  async (feedId: number, max: number = 50) => {
    // No `fulltext`: the API then truncates descriptions to ~100 words, which
    // is all a list needs. The episode page fetches full notes via episodes/byid.
    const data = await apiGet<{ items?: RawEpisode[] }>("episodes/byfeedid", { id: feedId, max });
    return (data.items ?? [])
      .map(toEpisode)
      .filter(keepEpisode)
      .map((e) => ({ ...e, description: e.description.slice(0, LIST_DESCRIPTION_MAX) }));
  },
);

export const getEpisode = cached("episode", REVALIDATE.detail, async (id: number) => {
  const data = await apiGet<{ episode?: RawEpisode | RawEpisode[] }>("episodes/byid", {
    id,
    fulltext: true,
  });
  const ep = single(data.episode);
  if (!ep?.id) return null;
  const episode = toEpisode(ep);
  episode.description = episode.description.slice(0, 200_000);
  return keepEpisode(episode) ? episode : null;
});

export const searchPodcasts = cached(
  "search",
  REVALIDATE.trending,
  async (q: string, max: number = 40) => {
    const data = await apiGet<{ feeds?: RawFeed[] }>("search/byterm", {
      q,
      max,
      similar: true,
    });
    return (data.feeds ?? []).map((f) => toPodcastSummary(f)).filter(keepPodcast);
  },
);

export const searchMusic = cached(
  "search-music",
  REVALIDATE.trending,
  async (q: string, max: number = 40) => {
    const data = await apiGet<{ feeds?: RawFeed[] }>("search/music/byterm", {
      q,
      max,
      similar: true,
    });
    return (data.feeds ?? []).map((f) => toPodcastSummary(f)).filter(keepPodcast);
  },
);

/** Runs a loader and turns "keys missing" / API failures into a tagged result. */
export async function load<T>(
  fn: () => Promise<T>,
): Promise<{ ok: true; data: T } | { ok: false; reason: "missing-keys" | "error" }> {
  try {
    return { ok: true, data: await fn() };
  } catch (error) {
    if (
      error instanceof MissingApiKeysError ||
      (error instanceof Error && error.name === "MissingApiKeysError") ||
      !isPodcastIndexConfigured()
    ) {
      return { ok: false, reason: "missing-keys" };
    }
    console.error("[podcastindex]", error instanceof Error ? error.message : error);
    return { ok: false, reason: "error" };
  }
}
