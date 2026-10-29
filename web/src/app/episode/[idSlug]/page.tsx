import type { Metadata } from "next";
import Link from "next/link";
import { notFound, permanentRedirect } from "next/navigation";
import { AiEpisodePanel } from "@/components/ai/AiEpisodePanel";
import { OpenInAppLink } from "@/components/AppBanner";
import { Artwork } from "@/components/Artwork";
import { Breadcrumbs } from "@/components/Breadcrumbs";
import { EpisodeList, SectionHeader } from "@/components/cards";
import { ExternalIcon } from "@/components/icons";
import { JsonLd } from "@/components/JsonLd";
import { LoadFailure, PodcastIndexCredit } from "@/components/notices";
import { PlayButton, QueueButton } from "@/components/player/PlayButton";
import { RichText } from "@/components/RichText";
import { isAiEnabled } from "@/lib/ai";
import { formatDate, formatDuration, isoDate, isoDuration } from "@/lib/format";
import { t } from "@/lib/i18n";
import { getEpisode, getEpisodesByFeed, load } from "@/lib/podcastindex";
import { NOINDEX, breadcrumbJsonLd, httpsOnly, isEpisodeIndexable, seoTitle } from "@/lib/seo";
import { STATIC_EXPORT, absoluteUrl } from "@/lib/site";
import { staticEpisode, staticEpisodeParams } from "@/lib/static-export";
import { episodePath, parseIdSlug, podcastPath, slugify } from "@/lib/slug";
import { htmlToParagraphs, htmlToText } from "@/lib/text";
import { episodeToTrack, snippet } from "@/lib/tracks";

export const revalidate = 3600;

export function generateStaticParams() {
  // Server build: no build-time list, pages render on first request and are cached (ISR).
  return staticEpisodeParams();
}

/** A static export has no per-episode API call: see staticEpisode(). */
function fetchEpisode(id: number) {
  return STATIC_EXPORT ? staticEpisode(id) : getEpisode(id);
}

export async function generateMetadata({ params }: PageProps<"/episode/[idSlug]">): Promise<Metadata> {
  const parsed = parseIdSlug((await params).idSlug);
  if (!parsed) return {};
  const res = await load(() => fetchEpisode(parsed.id));
  if (!res.ok || !res.data) return { robots: NOINDEX };
  const e = res.data;
  const description = snippet(htmlToText(e.description), 155) || `${e.title} — ${e.feedTitle}`;
  const path = episodePath(e);
  const image = e.image || e.feedImage;
  const audio = httpsOnly(e.enclosureUrl);
  return {
    title: { absolute: seoTitle(e.title) },
    description,
    alternates: { canonical: path },
    robots: isEpisodeIndexable(e) ? undefined : NOINDEX,
    openGraph: {
      type: "website",
      title: e.title,
      description,
      url: path,
      images: image ? [{ url: image, alt: e.title }] : undefined,
      audio: audio ? [{ url: audio, type: e.enclosureType || "audio/mpeg" }] : undefined,
    },
    twitter: { card: "summary_large_image", title: e.title, description, images: image ? [image] : undefined },
  };
}

export default async function EpisodePage({ params }: PageProps<"/episode/[idSlug]">) {
  const { idSlug } = await params;
  const parsed = parseIdSlug(idSlug);
  if (!parsed) notFound();

  const res = await load(() => fetchEpisode(parsed.id));
  if (!res.ok) return <LoadFailure reason={res.reason} />;
  const episode = res.data;
  if (!episode) notFound();
  if (parsed.slug !== slugify(episode.title)) permanentRedirect(episodePath(episode));

  const siblings = episode.feedId ? await load(() => getEpisodesByFeed(episode.feedId, 12)) : null;
  const more = siblings?.ok ? siblings.data.filter((e) => e.id !== episode.id).slice(0, 8) : [];
  const track = episodeToTrack(episode);
  const podcastHref = podcastPath({ id: episode.feedId, title: episode.feedTitle });
  const notes = htmlToParagraphs(episode.description);
  const meta = [
    formatDate(episode.datePublished),
    formatDuration(episode.duration),
    episode.season ? t.episode.season(episode.season) : "",
    episode.episode ? t.episode.number(episode.episode) : "",
  ].filter(Boolean);
  const aiEnabled = isAiEnabled();
  const transcript = episode.transcripts[0];
  const crumbs = [
    { name: t.nav.home, path: "/" },
    { name: episode.feedTitle || t.episode.fromPodcast, path: podcastHref },
    { name: episode.title, path: episodePath(episode) },
  ];

  return (
    <article className="space-y-8">
      <JsonLd
        data={{
          "@context": "https://schema.org",
          "@type": "PodcastEpisode",
          name: episode.title,
          url: absoluteUrl(episodePath(episode)),
          description: snippet(htmlToText(episode.description), 500),
          datePublished: isoDate(episode.datePublished),
          timeRequired: isoDuration(episode.duration),
          image: track.image || undefined,
          episodeNumber: episode.episode ?? undefined,
          associatedMedia: {
            "@type": "AudioObject",
            contentUrl: episode.enclosureUrl,
            encodingFormat: episode.enclosureType || undefined,
            duration: isoDuration(episode.duration),
          },
          partOfSeries: {
            "@type": "PodcastSeries",
            "@id": `${absoluteUrl(podcastHref)}#series`,
            name: episode.feedTitle,
            url: absoluteUrl(podcastHref),
          },
        }}
      />
      <JsonLd data={breadcrumbJsonLd(crumbs)} />
      <Breadcrumbs items={crumbs} />

      <header className="flex flex-col gap-5 sm:flex-row sm:items-end">
        <Artwork src={track.image} alt={episode.title} size={180} priority className="rounded-2xl shadow-md" />
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold text-primary">
            <Link href={podcastHref} className="hover:underline">
              {episode.feedTitle}
            </Link>
          </p>
          <h1 className="mt-1 text-xl font-bold tracking-tight sm:text-3xl">{episode.title}</h1>
          {meta.length > 0 && <p className="mt-1 text-xs text-subtle">{meta.join(" · ")}</p>}
          <div className="mt-4 flex flex-wrap items-center gap-2">
            <PlayButton track={track} variant="pill" />
            <QueueButton track={track} className="size-10" />
            <OpenInAppLink feedId={episode.feedId} />
            {transcript && (
              <a
                href={transcript.url}
                rel="nofollow noopener"
                target="_blank"
                title={t.episode.hasTranscript}
                className="inline-flex items-center gap-1.5 rounded-full px-3 py-2 text-sm font-medium text-muted hover:text-primary"
              >
                {t.episode.transcript} <ExternalIcon size={14} />
              </a>
            )}
          </div>
        </div>
      </header>

      <AiEpisodePanel
        episodeId={episode.id}
        enabled={aiEnabled}
        hasTranscript={episode.transcripts.length > 0}
      />

      {notes.length > 0 && (
        <section aria-labelledby="notes-title" className="max-w-3xl">
          <h2 id="notes-title" className="mb-2 text-lg font-bold">
            {t.episode.showNotes}
          </h2>
          <RichText paragraphs={notes} />
        </section>
      )}

      {more.length > 0 && (
        <section aria-labelledby="more-title">
          <SectionHeader id="more-title" title={t.episode.moreFromPodcast} href={podcastHref} />
          <EpisodeList episodes={more} showPodcast={false} />
        </section>
      )}

      <PodcastIndexCredit />
    </article>
  );
}
