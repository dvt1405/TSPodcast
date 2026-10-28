import type { Metadata } from "next";
import Link from "next/link";
import { notFound, permanentRedirect } from "next/navigation";
import { OpenInAppLink } from "@/components/AppBanner";
import { Artwork } from "@/components/Artwork";
import { Breadcrumbs } from "@/components/Breadcrumbs";
import { EpisodeList, SectionHeader } from "@/components/cards";
import { ExternalIcon } from "@/components/icons";
import { JsonLd } from "@/components/JsonLd";
import { EmptyState, LoadFailure, PodcastIndexCredit } from "@/components/notices";
import { PlayButton } from "@/components/player/PlayButton";
import { RichText } from "@/components/RichText";
import { categoryLabel, t } from "@/lib/i18n";
import { getEpisodesByFeed, getPodcast, load } from "@/lib/podcastindex";
import { NOINDEX, breadcrumbJsonLd, isPodcastIndexable, seoTitle } from "@/lib/seo";
import { absoluteUrl } from "@/lib/site";
import { categoryPath, episodePath, parseIdSlug, podcastPath, slugify } from "@/lib/slug";
import { htmlToParagraphs, htmlToText } from "@/lib/text";
import { episodeToTrack, snippet } from "@/lib/tracks";

export const revalidate = 3600;

/** Same arguments as the page so the cached call is shared. */
const EPISODES_MAX = 50;

export function generateStaticParams() {
  // No build-time list: pages render on first request, then are cached (ISR).
  return [];
}

export async function generateMetadata({ params }: PageProps<"/podcast/[idSlug]">): Promise<Metadata> {
  const parsed = parseIdSlug((await params).idSlug);
  if (!parsed) return {};
  const res = await load(() => getPodcast(parsed.id));
  if (!res.ok || !res.data) return { robots: NOINDEX };
  const p = res.data;
  const episodes = await load(() => getEpisodesByFeed(p.id, EPISODES_MAX));
  const description = snippet(htmlToText(p.description), 155) || `${p.title} — ${p.author}`;
  const path = podcastPath(p);
  return {
    title: { absolute: seoTitle(p.title) },
    description,
    alternates: { canonical: path },
    robots: episodes.ok && isPodcastIndexable(p, episodes.data) ? undefined : NOINDEX,
    openGraph: {
      type: "website",
      title: p.title,
      description,
      url: path,
      images: p.image ? [{ url: p.image, alt: p.title }] : undefined,
    },
    twitter: { card: "summary_large_image", title: p.title, description, images: p.image ? [p.image] : undefined },
  };
}

export default async function PodcastPage({ params }: PageProps<"/podcast/[idSlug]">) {
  const { idSlug } = await params;
  const parsed = parseIdSlug(idSlug);
  if (!parsed) notFound();

  const res = await load(() => getPodcast(parsed.id));
  if (!res.ok) return <LoadFailure reason={res.reason} />;
  const podcast = res.data;
  if (!podcast) notFound();
  // The id is the source of truth; a wrong or stale slug 301s to the canonical URL.
  if (parsed.slug !== slugify(podcast.title)) permanentRedirect(podcastPath(podcast));

  const episodes = await load(() => getEpisodesByFeed(podcast.id, EPISODES_MAX));
  const list = episodes.ok ? episodes.data : [];
  const latest = list[0];
  const queue = list.map((e) => episodeToTrack(e, podcast.title));
  const url = absoluteUrl(podcastPath(podcast));
  const firstCategory = podcast.categories[0];
  const crumbs = [
    { name: t.nav.home, path: "/" },
    firstCategory
      ? { name: categoryLabel(firstCategory), path: categoryPath(firstCategory) }
      : { name: t.nav.trending, path: "/trending" },
    { name: podcast.title, path: podcastPath(podcast) },
  ];

  return (
    <article className="space-y-8">
      <JsonLd
        data={{
          "@context": "https://schema.org",
          "@type": "PodcastSeries",
          "@id": `${url}#series`,
          name: podcast.title,
          url,
          description: snippet(htmlToText(podcast.description), 500),
          image: podcast.image || undefined,
          webFeed: podcast.feedUrl || undefined,
          inLanguage: podcast.language || undefined,
          author: podcast.author ? { "@type": "Person", name: podcast.author } : undefined,
          genre: podcast.categories.length ? podcast.categories : undefined,
          potentialAction: { "@type": "ListenAction", target: url },
          episode: list.slice(0, 10).map((e) => ({
            "@type": "PodcastEpisode",
            name: e.title,
            url: absoluteUrl(episodePath(e)),
            datePublished: e.datePublished ? new Date(e.datePublished * 1000).toISOString() : undefined,
          })),
        }}
      />
      <JsonLd data={breadcrumbJsonLd(crumbs)} />
      <Breadcrumbs items={crumbs} />

      <header className="flex flex-col gap-5 sm:flex-row sm:items-end">
        <Artwork src={podcast.image} alt={podcast.title} size={200} priority className="rounded-2xl shadow-md" />
        <div className="min-w-0 flex-1">
          <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">{podcast.title}</h1>
          {podcast.author && <p className="mt-1 text-sm font-medium text-primary">{podcast.author}</p>}
          <p className="mt-1 text-xs text-subtle">
            {[
              podcast.episodeCount ? t.podcast.episodeCount(podcast.episodeCount) : "",
              podcast.language,
              podcast.explicit ? t.podcast.explicit : "",
            ]
              .filter(Boolean)
              .join(" · ")}
          </p>
          {podcast.categories.length > 0 && (
            <ul className="mt-3 flex flex-wrap gap-1.5">
              {podcast.categories.slice(0, 6).map((c) => (
                <li key={c}>
                  <Link
                    href={categoryPath(c)}
                    className="rounded-full bg-primary-soft px-2.5 py-1 text-xs font-medium text-primary hover:underline"
                  >
                    {categoryLabel(c)}
                  </Link>
                </li>
              ))}
            </ul>
          )}
          <div className="mt-4 flex flex-wrap items-center gap-2">
            {latest && (
              <PlayButton
                track={episodeToTrack(latest, podcast.title)}
                queue={queue}
                variant="pill"
                label={t.podcast.playLatest}
              />
            )}
            <OpenInAppLink feedId={podcast.id} />
            {podcast.link && /^https?:\/\//.test(podcast.link) && (
              <a
                href={podcast.link}
                rel="nofollow noopener"
                target="_blank"
                className="inline-flex items-center gap-1.5 rounded-full px-3 py-2 text-sm font-medium text-muted hover:text-primary"
              >
                {t.podcast.website} <ExternalIcon size={14} />
              </a>
            )}
            {podcast.feedUrl && (
              <a
                href={podcast.feedUrl}
                rel="nofollow noopener"
                target="_blank"
                className="inline-flex items-center gap-1.5 rounded-full px-3 py-2 text-sm font-medium text-muted hover:text-primary"
              >
                {t.podcast.rss} <ExternalIcon size={14} />
              </a>
            )}
          </div>
        </div>
      </header>

      {podcast.description && <RichText paragraphs={htmlToParagraphs(podcast.description)} className="max-w-3xl" />}

      <section aria-labelledby="episodes-title">
        <SectionHeader id="episodes-title" title={t.podcast.episodes} />
        {episodes.ok ? (
          list.length ? (
            <EpisodeList episodes={list} showPodcast={false} podcastTitle={podcast.title} asQueue />
          ) : (
            <EmptyState message={t.podcast.noEpisodes} />
          )
        ) : (
          <LoadFailure reason={episodes.reason} />
        )}
      </section>

      <PodcastIndexCredit />
    </article>
  );
}
