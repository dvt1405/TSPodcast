// Normalized shapes passed from server code to components. Plain JSON only,
// so they can cross the server/client boundary and live in unstable_cache.

export interface PodcastSummary {
  id: number;
  title: string;
  author: string;
  image: string;
  description: string;
  language: string;
  categories: string[];
  feedUrl?: string;
  trendScore?: number;
  newestItemPublishTime?: number;
}

export interface PodcastDetail extends PodcastSummary {
  link: string;
  ownerName: string;
  episodeCount: number;
  explicit: boolean;
  medium: string;
  podcastGuid: string;
  itunesId: number | null;
  lastUpdateTime: number;
  funding?: { url: string; message: string } | null;
}

export interface Transcript {
  url: string;
  type: string;
}

export interface Episode {
  id: number;
  title: string;
  description: string;
  link: string;
  guid: string;
  datePublished: number;
  enclosureUrl: string;
  enclosureType: string;
  duration: number;
  explicit: boolean;
  episode: number | null;
  season: number | null;
  image: string;
  feedId: number;
  feedTitle: string;
  feedImage: string;
  feedLanguage: string;
  transcripts: Transcript[];
  chaptersUrl: string;
}

export interface Category {
  id: number;
  name: string;
}

export interface RadioChannel {
  id: string;
  name: string;
  subtitle: string;
  logo: string;
  streamUrl: string;
  source: "VOV" | "VOH";
  pageUrl: string;
}

/** What the player queues. Shared by episodes and radio. */
export interface Track {
  /** Stable key, e.g. "ep:123" or "radio:vov1". */
  key: string;
  title: string;
  subtitle: string;
  image: string;
  src: string;
  /** Page to open when the title is clicked. */
  href?: string;
  live?: boolean;
  duration?: number;
}
