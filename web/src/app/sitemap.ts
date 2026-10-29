import type { MetadataRoute } from "next";
import { getCategories, getTrending, isPodcastIndexConfigured } from "@/lib/podcastindex";
import { isPodcastSummaryIndexable } from "@/lib/seo";
import { absoluteUrl } from "@/lib/site";
import { categoryPath, podcastPath } from "@/lib/slug";
import type { PodcastSummary } from "@/lib/types";

export const revalidate = 3600;

// Only URLs that pass the index thresholds (seo.ts) and resolve 200 at their
// canonical path. lastmod is the content date, never "now".
export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const entries: MetadataRoute.Sitemap = [
    { url: absoluteUrl("/"), changeFrequency: "hourly", priority: 1 },
    { url: absoluteUrl("/trending"), changeFrequency: "hourly", priority: 0.8 },
    { url: absoluteUrl("/radio"), changeFrequency: "weekly", priority: 0.5 },
  ];
  if (!isPodcastIndexConfigured()) return entries;

  try {
    const [categories, vi, global] = await Promise.all([
      getCategories(),
      getTrending({ max: 100, lang: "vi" }),
      getTrending({ max: 100 }),
    ]);
    for (const c of categories) {
      entries.push({ url: absoluteUrl(categoryPath(c.name)), changeFrequency: "daily", priority: 0.6 });
    }
    const seen = new Set<number>();
    const podcasts: PodcastSummary[] = [];
    for (const p of [...vi, ...global]) {
      if (!seen.has(p.id) && isPodcastSummaryIndexable(p)) {
        seen.add(p.id);
        podcasts.push(p);
      }
    }
    for (const p of podcasts) {
      entries.push({
        url: absoluteUrl(podcastPath(p)),
        lastModified: p.newestItemPublishTime ? new Date(p.newestItemPublishTime * 1000) : undefined,
        changeFrequency: "daily",
        priority: 0.7,
      });
    }
  } catch (error) {
    console.error("[sitemap]", error instanceof Error ? error.message : error);
  }
  return entries;
}
