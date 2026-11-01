import type { Metadata } from "next";
import Link from "next/link";
import { AiSearch } from "@/components/ai/AiSearch";
import { PodcastGrid, SectionHeader } from "@/components/cards";
import { EmptyState, LoadFailure } from "@/components/notices";
import { SearchForm } from "@/components/SearchForm";
import { isAiEnabled } from "@/lib/ai";
import { t } from "@/lib/i18n";
import { getMusicFeeds, isPodcastIndexConfigured, load, searchMusic, searchPodcasts } from "@/lib/podcastindex";
import { NOINDEX } from "@/lib/seo";

// Search result pages are thin and unbounded: keep them out of the index but
// let crawlers follow the links (docs/in-progress/plans/seo-web-dashboard.md §A.3/A.5).
export const metadata: Metadata = {
  title: t.search.title,
  robots: NOINDEX,
  alternates: { canonical: "/search" },
};

type Mode = "podcast" | "music" | "ai";

function first(v: string | string[] | undefined): string {
  return (Array.isArray(v) ? v[0] : v)?.trim().slice(0, 300) ?? "";
}

// Not PageProps<"/search">: the static export build leaves this route out, so
// "/search" is not in its generated route types.
type SearchPageProps = { searchParams: Promise<Record<string, string | string[] | undefined>> };

export default async function SearchPage({ searchParams }: SearchPageProps) {
  const params = await searchParams;
  const q = first(params.q);
  const aiEnabled = isAiEnabled();
  const requested = first(params.mode);
  const mode: Mode = requested === "music" ? "music" : requested === "ai" && aiEnabled ? "ai" : "podcast";

  const tabs: { mode: Mode; label: string }[] = [
    { mode: "podcast", label: t.search.modePodcast },
    { mode: "music", label: t.search.modeMusic },
    ...(aiEnabled ? [{ mode: "ai" as const, label: t.search.modeAi }] : []),
  ];
  const tabHref = (m: Mode) => `/search?${new URLSearchParams({ ...(q ? { q } : {}), mode: m })}`;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold tracking-tight">{t.search.title}</h1>
      <SearchForm defaultValue={q} aiEnabled={aiEnabled} mode={mode} autoFocus={!q} />
      <nav aria-label={t.search.title}>
        <ul className="flex gap-2">
          {tabs.map((tab) => (
            <li key={tab.mode}>
              <Link
                href={tabHref(tab.mode)}
                aria-current={tab.mode === mode ? "page" : undefined}
                className={`inline-block rounded-full px-4 py-1.5 text-sm font-semibold ${
                  tab.mode === mode ? "bg-primary text-on-primary" : "bg-surface text-muted hover:text-primary"
                }`}
              >
                {tab.label}
              </Link>
            </li>
          ))}
        </ul>
      </nav>
      {mode === "ai" && <p className="text-sm text-subtle">{t.ai.searchIntro}</p>}

      {!isPodcastIndexConfigured() ? (
        <LoadFailure reason="missing-keys" />
      ) : mode === "ai" ? (
        q.length >= 2 ? <AiSearch query={q} /> : <EmptyState message={t.search.prompt} />
      ) : (
        <Results q={q} mode={mode} />
      )}
    </div>
  );
}

async function Results({ q, mode }: { q: string; mode: "podcast" | "music" }) {
  if (!q) {
    if (mode === "podcast") return <EmptyState message={t.search.prompt} />;
    // Music tab without a query shows the podcast:medium=music library.
    const library = await load(() => getMusicFeeds(40));
    return (
      <section aria-labelledby="music-lib">
        <SectionHeader id="music-lib" title={t.home.music} subtitle={t.home.musicSubtitle} />
        {library.ok ? <PodcastGrid podcasts={library.data} /> : <LoadFailure reason={library.reason} />}
      </section>
    );
  }

  const res = await load(() => (mode === "music" ? searchMusic(q, 40) : searchPodcasts(q, 40)));
  return (
    <section aria-labelledby="results-title">
      <SectionHeader id="results-title" title={mode === "music" ? t.search.musicResults(q) : t.search.results(q)} />
      {!res.ok ? (
        <LoadFailure reason={res.reason} />
      ) : res.data.length ? (
        <PodcastGrid podcasts={res.data} />
      ) : (
        <EmptyState message={mode === "music" ? `${t.search.empty} ${t.search.musicEmptyHint}` : t.search.empty} />
      )}
    </section>
  );
}
