import Link from "next/link";
import { CategoryChips, EpisodeList, PodcastShelf, SectionHeader } from "@/components/cards";
import { RadioIcon } from "@/components/icons";
import { LoadFailure } from "@/components/notices";
import { SearchForm } from "@/components/SearchForm";
import { isAiEnabled } from "@/lib/ai";
import { t } from "@/lib/i18n";
import {
  getCategories,
  getMusicFeeds,
  getRecentEpisodes,
  getTrending,
  load,
} from "@/lib/podcastindex";
import type { PodcastSummary } from "@/lib/types";

export const revalidate = 900;

/** Categories surfaced on the home page, in this order, when the API has them. */
const FEATURED_CATEGORIES = [
  "News", "Business", "Technology", "Education", "Comedy", "Society", "Health",
  "Sports", "History", "Science", "Music", "Arts", "Kids", "True Crime", "Religion", "Leisure",
];

async function getHomeTrending(): Promise<PodcastSummary[]> {
  const [vi, global] = await Promise.all([
    getTrending({ max: 24, lang: "vi" }),
    getTrending({ max: 24 }),
  ]);
  // Vietnamese first; top up with global trending when the vi list is thin.
  const seen = new Set(vi.map((p) => p.id));
  return [...vi, ...global.filter((p) => !seen.has(p.id))].slice(0, 18);
}

export default async function HomePage() {
  const aiEnabled = isAiEnabled();
  const [trending, music, categories, recent] = await Promise.all([
    load(getHomeTrending),
    load(() => getMusicFeeds(24)),
    load(getCategories),
    load(() => getRecentEpisodes(12)),
  ]);

  const missingKeys = !trending.ok && trending.reason === "missing-keys";
  const categoryNames = categories.ok
    ? FEATURED_CATEGORIES.filter((n) => categories.data.some((c) => c.name === n))
    : [];

  return (
    <div className="space-y-10">
      <section
        aria-labelledby="hero-title"
        className="rounded-3xl bg-gradient-to-br from-primary to-secondary px-5 py-8 text-white shadow-sm sm:px-10 sm:py-12"
      >
        <h1 id="hero-title" className="text-2xl font-bold tracking-tight sm:text-4xl">
          {t.home.heroTitle}
        </h1>
        <p className="mt-2 max-w-2xl text-sm text-white/90 sm:text-base">{t.home.heroSubtitle}</p>
        <div className="mt-5 max-w-2xl text-foreground">
          <SearchForm aiEnabled={aiEnabled} id="hero-q" />
        </div>
      </section>

      {missingKeys ? (
        <LoadFailure reason="missing-keys" />
      ) : (
        <>
          <section aria-labelledby="trending-title">
            <SectionHeader
              id="trending-title"
              title={t.home.trending}
              subtitle={t.home.trendingSubtitle}
              href="/trending"
            />
            {trending.ok ? <PodcastShelf podcasts={trending.data} priorityCount={4} /> : <LoadFailure reason={trending.reason} />}
          </section>

          {categoryNames.length > 0 && (
            <section aria-labelledby="categories-title">
              <SectionHeader id="categories-title" title={t.home.categories} />
              <CategoryChips names={categoryNames} />
            </section>
          )}

          <section aria-labelledby="music-title">
            <SectionHeader
              id="music-title"
              title={t.home.music}
              subtitle={t.home.musicSubtitle}
              href="/search?mode=music"
            />
            {music.ok ? <PodcastShelf podcasts={music.data.slice(0, 18)} /> : <LoadFailure reason={music.reason} />}
          </section>

          <section aria-labelledby="recent-title">
            <SectionHeader id="recent-title" title={t.home.recent} subtitle={t.home.recentSubtitle} />
            {recent.ok ? <EpisodeList episodes={recent.data} /> : <LoadFailure reason={recent.reason} />}
          </section>
        </>
      )}

      <section aria-labelledby="radio-title" className="rounded-2xl border border-border bg-surface p-5">
        <div className="flex items-center gap-4">
          <span className="inline-flex size-12 shrink-0 items-center justify-center rounded-2xl bg-primary-soft text-primary">
            <RadioIcon size={24} />
          </span>
          <div className="min-w-0 flex-1">
            <h2 id="radio-title" className="font-bold">
              {t.radio.title}
            </h2>
            <p className="text-sm text-subtle">{t.radio.description}</p>
          </div>
          <Link href="/radio" className="shrink-0 rounded-full bg-primary px-4 py-2 text-sm font-semibold text-on-primary">
            {t.nav.radio}
          </Link>
        </div>
      </section>
    </div>
  );
}
