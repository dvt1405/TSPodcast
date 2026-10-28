import { episodePath } from "./slug";
import type { Episode, RadioChannel, Track } from "./types";

export function episodeToTrack(e: Episode, podcastTitle?: string): Track {
  return {
    key: `ep:${e.id}`,
    title: e.title,
    subtitle: e.feedTitle || podcastTitle || "",
    image: e.image || e.feedImage,
    src: e.enclosureUrl,
    href: episodePath(e),
    duration: e.duration || undefined,
  };
}

export function radioToTrack(c: RadioChannel): Track {
  return {
    key: `radio:${c.id}`,
    title: c.name,
    subtitle: c.subtitle ? `${c.source} · ${c.subtitle}` : c.source,
    image: c.logo,
    src: c.streamUrl,
    href: "/radio",
    live: true,
  };
}

const ENTITIES: Record<string, string> = {
  amp: "&",
  lt: "<",
  gt: ">",
  quot: '"',
  apos: "'",
  nbsp: " ",
  "#39": "'",
};

/** Cheap tag stripper for one-line snippets rendered as text (never as HTML). */
export function snippet(html: string, max = 180): string {
  const text = html
    .replace(/<[^>]*>/g, " ")
    .replace(/&(#?\w+);/g, (m, name: string) => ENTITIES[name] ?? m)
    .replace(/\s+/g, " ")
    .trim();
  if (text.length <= max) return text;
  const cut = text.slice(0, max);
  const space = cut.lastIndexOf(" ");
  return `${(space > max * 0.6 ? cut.slice(0, space) : cut).trimEnd()}…`;
}
