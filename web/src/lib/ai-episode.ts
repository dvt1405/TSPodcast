import "server-only";
import { LruCache } from "./ai";
import { formatDate, formatDuration } from "./format";
import { getEpisode, getPodcast } from "./podcastindex";
import { htmlToText } from "./text";
import { fetchTranscriptText } from "./transcript";

/**
 * Upper bound on transcript characters sent to the model (~100k tokens). Long
 * shows can exceed it; when that happens the prompt says so explicitly rather
 * than letting the model assume it saw everything.
 */
const MAX_TRANSCRIPT_CHARS = 400_000;

export interface EpisodeContext {
  episodeId: number;
  title: string;
  hasTranscript: boolean;
  transcriptTruncated: boolean;
  /** The <episode> document given to the model. */
  document: string;
}

const contextCache = new LruCache<EpisodeContext>(200, 60 * 60 * 1000);

export async function getEpisodeContext(episodeId: number): Promise<EpisodeContext | null> {
  const hit = contextCache.get(String(episodeId));
  if (hit) return hit;

  const episode = await getEpisode(episodeId);
  if (!episode) return null;
  const podcast = episode.feedId ? await getPodcast(episode.feedId).catch(() => null) : null;

  let transcript = episode.transcripts.length ? await fetchTranscriptText(episode.transcripts) : null;
  let transcriptTruncated = false;
  if (transcript && transcript.length > MAX_TRANSCRIPT_CHARS) {
    transcript = transcript.slice(0, MAX_TRANSCRIPT_CHARS);
    transcriptTruncated = true;
  }

  const lines = [
    `<episode>`,
    `<title>${episode.title}</title>`,
    `<podcast>${episode.feedTitle || podcast?.title || ""}</podcast>`,
    podcast?.author ? `<author>${podcast.author}</author>` : "",
    episode.datePublished ? `<published>${formatDate(episode.datePublished)}</published>` : "",
    episode.duration ? `<duration>${formatDuration(episode.duration)} (${episode.duration} seconds)</duration>` : "",
    podcast?.description ? `<podcast_description>\n${htmlToText(podcast.description)}\n</podcast_description>` : "",
    `<show_notes>\n${htmlToText(episode.description) || "(none)"}\n</show_notes>`,
    transcript
      ? `<transcript${transcriptTruncated ? ' truncated="true" note="only the first part of the transcript is included"' : ""}>\n${transcript}\n</transcript>`
      : `<transcript>(not available - the feed publishes no podcast:transcript for this episode)</transcript>`,
    `</episode>`,
  ].filter(Boolean);

  const ctx: EpisodeContext = {
    episodeId,
    title: episode.title,
    hasTranscript: Boolean(transcript),
    transcriptTruncated,
    document: lines.join("\n"),
  };
  contextCache.set(String(episodeId), ctx);
  return ctx;
}
