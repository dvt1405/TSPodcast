import type { Metadata } from "next";
import { locale } from "./i18n";
import { SITE_NAME, absoluteUrl } from "./site";
import { htmlToText } from "./text";
import type { Episode, PodcastDetail, PodcastSummary } from "./types";

// Index-quality rules from docs/in-progress/plans/seo-web-dashboard.md §A.5. Pages that
// miss them still render for people but get `noindex, follow` and stay out of
// the sitemap, so millions of thin PodcastIndex feeds don't dilute the site.

const YEAR_SECONDS = 365 * 24 * 3600;
const INDEXABLE_LANGUAGES = ["vi", "en"];

export const NOINDEX: Metadata["robots"] = { index: false, follow: true };

function languageOk(language: string): boolean {
  const l = language.toLowerCase();
  return INDEXABLE_LANGUAGES.some((x) => l === x || l.startsWith(`${x}-`));
}

function recent(unixSeconds: number | undefined | null): boolean {
  return Boolean(unixSeconds) && Date.now() / 1000 - Number(unixSeconds) < YEAR_SECONDS;
}

/** vi/en, at least 3 episodes, and an episode within the last 12 months. */
export function isPodcastIndexable(p: PodcastDetail, episodes: Episode[]): boolean {
  const count = Math.max(p.episodeCount, episodes.length);
  const newest = Math.max(p.newestItemPublishTime ?? 0, episodes[0]?.datePublished ?? 0);
  return languageOk(p.language) && count >= 3 && recent(newest);
}

/** Sitemap pre-filter for list items, which carry no episode count. */
export function isPodcastSummaryIndexable(p: PodcastSummary): boolean {
  return languageOk(p.language) && recent(p.newestItemPublishTime);
}

/** At least 200 characters of show notes after stripping HTML, or a transcript. */
export function isEpisodeIndexable(e: Episode): boolean {
  return e.transcripts.length > 0 || htmlToText(e.description).length >= 200;
}

/** "<Podcast> | Nghe podcast trên TSPodcast", kept within ~60 characters. */
export function seoTitle(name: string): string {
  const suffix = locale === "vi" ? ` | Nghe podcast trên ${SITE_NAME}` : ` | Listen on ${SITE_NAME}`;
  const room = Math.max(60 - suffix.length, 16);
  if (name.length <= room) return `${name}${suffix}`;
  const cut = name.slice(0, room - 1);
  const space = cut.lastIndexOf(" ");
  return `${(space > room * 0.5 ? cut.slice(0, space) : cut).trimEnd()}…${suffix}`;
}

export function breadcrumbJsonLd(items: { name: string; path: string }[]) {
  return {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    itemListElement: items.map((item, i) => ({
      "@type": "ListItem",
      position: i + 1,
      name: item.name,
      item: absoluteUrl(item.path),
    })),
  };
}

/** og:audio only for https enclosures (§A.9). */
export function httpsOnly(url: string | null | undefined): string | undefined {
  return url && url.startsWith("https://") ? url : undefined;
}
