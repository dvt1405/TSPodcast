import "server-only";
import { getCategories, getEpisodesByFeed, getMusicFeeds, getRecentEpisodes, getTrending } from "./podcastindex";
import { STATIC_EXPORT } from "./site";
import type { Episode } from "./types";
import { episodePath, podcastPath, slugify } from "./slug";

// Which dynamic pages a static export pre-renders. A static host has no
// fallback renderer, so every page a visible link points to must be built here.
// The set is bounded (featured categories, their top podcasts, the home and
// trending lists). Episode pages are filled from the per-podcast episode lists
// rather than one episodes/byid call each, which keeps a build to a few hundred
// PodcastIndex calls (thousands of calls in a burst get the IP blocked).

/** Categories surfaced on the home page, in this order, when the API has them. */
export const FEATURED_CATEGORIES = [
  "News", "Business", "Technology", "Education", "Comedy", "Society", "Health",
  "Sports", "History", "Science", "Music", "Arts", "Kids", "True Crime", "Religion", "Leisure",
];

/** Podcasts per category page in a static export (48 when server-rendered). */
export const STATIC_CATEGORY_MAX = 12;

/** Episodes listed, and pre-rendered, per podcast in a static export (50 when server-rendered). */
export const STATIC_EPISODES_MAX = 12;

/** Category chips to show: only the pre-rendered ones in a static export. */
export function visibleCategoryNames(all: string[]): string[] {
  return STATIC_EXPORT ? FEATURED_CATEGORIES.filter((n) => all.includes(n)) : all;
}

type Ref = { id: number; title?: string | null };

let podcastsPromise: Promise<Ref[]> | null = null;

/** Podcasts linked from the home, trending and featured category pages. */
function staticPodcasts(): Promise<Ref[]> {
  podcastsPromise ??= (async () => {
    // Same arguments as the pages, so these calls share their cache entries.
    const [viHome, globalHome, viTrending, globalTrending, music, recent, categories] = await Promise.all([
      getTrending({ max: 24, lang: "vi" }),
      getTrending({ max: 24 }),
      getTrending({ max: 36, lang: "vi" }),
      getTrending({ max: 36 }),
      getMusicFeeds(24),
      getRecentEpisodes(12),
      getCategories(),
    ]);
    const names = visibleCategoryNames(categories.map((c) => c.name));
    const byCategory = await Promise.all(names.map((cat) => getTrending({ max: STATIC_CATEGORY_MAX, cat })));
    const all: Ref[] = [
      ...viHome, ...globalHome, ...viTrending, ...globalTrending, ...music, ...byCategory.flat(),
      ...recent.map((e) => ({ id: e.feedId, title: e.feedTitle })),
    ];
    const seen = new Map<number, Ref>();
    for (const p of all) if (p.id && !seen.has(p.id)) seen.set(p.id, p);
    return [...seen.values()];
  })();
  return podcastsPromise;
}

function segment(path: string): string {
  return path.slice(path.lastIndexOf("/") + 1);
}

export async function staticCategoryParams(): Promise<{ slug: string }[]> {
  if (!STATIC_EXPORT) return [];
  const names = visibleCategoryNames((await getCategories()).map((c) => c.name));
  return names.map((name) => ({ slug: slugify(name) }));
}

export async function staticPodcastParams(): Promise<{ idSlug: string }[]> {
  if (!STATIC_EXPORT) return [];
  return (await staticPodcasts()).map((p) => ({ idSlug: segment(podcastPath(p)) }));
}

let episodesPromise: Promise<Map<number, Episode>> | null = null;

/** Every pre-rendered episode, from the home list and each podcast's latest episodes. */
function staticEpisodes(): Promise<Map<number, Episode>> {
  episodesPromise ??= (async () => {
    const podcasts = await staticPodcasts();
    const episodes: Episode[] = [...(await getRecentEpisodes(12))];
    // Same arguments as the podcast page, so its cached list is reused. Small
    // batches: this is the bulk of the build's PodcastIndex traffic.
    for (let i = 0; i < podcasts.length; i += 4) {
      const batch = await Promise.all(
        podcasts.slice(i, i + 4).map((p) => getEpisodesByFeed(p.id, STATIC_EPISODES_MAX).catch(() => [])),
      );
      episodes.push(...batch.flat());
    }
    return new Map(episodes.map((e) => [e.id, e]));
  })();
  return episodesPromise;
}

/**
 * Episode for a static export page. List items carry show notes cut to the
 * list length instead of the full text episodes/byid returns.
 */
export async function staticEpisode(id: number): Promise<Episode | null> {
  return (await staticEpisodes()).get(id) ?? null;
}

export async function staticEpisodeParams(): Promise<{ idSlug: string }[]> {
  if (!STATIC_EXPORT) return [];
  const episodes = [...(await staticEpisodes()).values()];
  const slugs = new Set(episodes.map((e) => segment(episodePath(e))));
  return [...slugs].map((idSlug) => ({ idSlug }));
}
