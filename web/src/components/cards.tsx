import Link from "next/link";
import { Artwork } from "@/components/Artwork";
import { PlayButton, QueueButton } from "@/components/player/PlayButton";
import { formatDate, formatDuration } from "@/lib/format";
import { categoryLabel, t } from "@/lib/i18n";
import { categoryPath, podcastPath } from "@/lib/slug";
import { episodeToTrack, snippet } from "@/lib/tracks";
import type { Episode, PodcastSummary, Track } from "@/lib/types";

export function SectionHeader({
  id,
  title,
  subtitle,
  href,
  linkLabel = t.home.seeAll,
}: {
  id?: string;
  title: string;
  subtitle?: string;
  href?: string;
  linkLabel?: string;
}) {
  return (
    <div className="mb-3 flex items-end justify-between gap-4">
      <div className="min-w-0">
        <h2 id={id} className="text-lg font-bold tracking-tight sm:text-xl">
          {title}
        </h2>
        {subtitle && <p className="mt-0.5 text-sm text-subtle">{subtitle}</p>}
      </div>
      {href && (
        <Link href={href} className="shrink-0 text-sm font-semibold text-primary hover:underline">
          {linkLabel}
        </Link>
      )}
    </div>
  );
}

export function PodcastCard({ podcast, priority = false }: { podcast: PodcastSummary; priority?: boolean }) {
  return (
    <Link
      href={podcastPath(podcast)}
      className="group block rounded-xl p-1.5 transition hover:bg-surface focus-visible:bg-surface"
    >
      <Artwork
        src={podcast.image}
        alt=""
        fluid
        priority={priority}
        className="rounded-xl shadow-sm transition group-hover:shadow-md"
      />
      <h3 className="mt-2 line-clamp-2 text-sm font-semibold leading-snug">{podcast.title}</h3>
      {podcast.author && <p className="mt-0.5 line-clamp-1 text-xs text-subtle">{podcast.author}</p>}
    </Link>
  );
}

/** Horizontal, snap-scrolling row on phones; wraps into a grid from md up. */
export function PodcastShelf({ podcasts, priorityCount = 0 }: { podcasts: PodcastSummary[]; priorityCount?: number }) {
  return (
    <ul className="shelf -mx-4 grid auto-cols-[42%] grid-flow-col gap-2 overflow-x-auto px-4 pb-2 sm:auto-cols-[28%] md:mx-0 md:grid-flow-row md:grid-cols-5 md:overflow-visible md:px-0 lg:grid-cols-6">
      {podcasts.map((p, i) => (
        <li key={p.id}>
          <PodcastCard podcast={p} priority={i < priorityCount} />
        </li>
      ))}
    </ul>
  );
}

export function PodcastGrid({ podcasts }: { podcasts: PodcastSummary[] }) {
  return (
    <ul className="grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6">
      {podcasts.map((p, i) => (
        <li key={p.id}>
          <PodcastCard podcast={p} priority={i < 6} />
        </li>
      ))}
    </ul>
  );
}

export function EpisodeRow({
  episode,
  queue,
  showPodcast = true,
  podcastTitle,
}: {
  episode: Episode;
  queue?: Track[];
  showPodcast?: boolean;
  podcastTitle?: string;
}) {
  const track = episodeToTrack(episode, podcastTitle);
  const meta = [formatDate(episode.datePublished), formatDuration(episode.duration)].filter(Boolean).join(" · ");
  const desc = snippet(episode.description, 160);
  return (
    <li className="flex gap-3 rounded-xl p-2 transition hover:bg-surface">
      <Link href={track.href!} className="shrink-0" tabIndex={-1} aria-hidden="true">
        <Artwork src={track.image} alt="" size={72} className="rounded-lg" />
      </Link>
      <div className="min-w-0 flex-1">
        <h3 className="line-clamp-2 text-sm font-semibold leading-snug sm:text-base">
          <Link href={track.href!} className="hover:underline">
            {episode.title}
          </Link>
        </h3>
        {showPodcast && episode.feedTitle && (
          <p className="mt-0.5 line-clamp-1 text-xs font-medium text-primary">
            <Link href={podcastPath({ id: episode.feedId, title: episode.feedTitle })} className="hover:underline">
              {episode.feedTitle}
            </Link>
          </p>
        )}
        {meta && <p className="mt-0.5 text-xs text-subtle">{meta}</p>}
        {desc && <p className="mt-1 line-clamp-2 hidden text-xs text-muted sm:block">{desc}</p>}
      </div>
      <div className="flex shrink-0 flex-col items-center gap-2 sm:flex-row sm:self-center">
        <PlayButton track={track} queue={queue} />
        <QueueButton track={track} />
      </div>
    </li>
  );
}

export function EpisodeList({
  episodes,
  showPodcast = true,
  podcastTitle,
  asQueue = false,
}: {
  episodes: Episode[];
  showPodcast?: boolean;
  podcastTitle?: string;
  /** Playing an item queues the rest of the list after it. */
  asQueue?: boolean;
}) {
  const queue = asQueue ? episodes.map((e) => episodeToTrack(e, podcastTitle)) : undefined;
  return (
    <ul className="-mx-2 divide-y divide-border/60">
      {episodes.map((e) => (
        <EpisodeRow key={e.id} episode={e} queue={queue} showPodcast={showPodcast} podcastTitle={podcastTitle} />
      ))}
    </ul>
  );
}

export function CategoryChips({ names, current }: { names: string[]; current?: string }) {
  return (
    <ul className="flex flex-wrap gap-2">
      {names.map((name) => {
        const active = current === name;
        return (
          <li key={name}>
            <Link
              href={categoryPath(name)}
              aria-current={active ? "page" : undefined}
              className={`inline-block rounded-full border px-3.5 py-1.5 text-sm font-medium transition ${
                active
                  ? "border-primary bg-primary text-on-primary"
                  : "border-border bg-surface hover:border-primary hover:text-primary"
              }`}
            >
              {categoryLabel(name)}
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
