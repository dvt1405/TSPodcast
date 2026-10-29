import type { Metadata } from "next";
import { CategoryChips, PodcastGrid, SectionHeader } from "@/components/cards";
import { JsonLd } from "@/components/JsonLd";
import { EmptyState, LoadFailure } from "@/components/notices";
import { t } from "@/lib/i18n";
import { getCategories, getTrending, load } from "@/lib/podcastindex";
import { OG_IMAGES, absoluteUrl } from "@/lib/site";
import { podcastPath } from "@/lib/slug";

export const revalidate = 900;

export const metadata: Metadata = {
  title: t.trending.title,
  description: t.trending.description,
  alternates: { canonical: "/trending" },
  openGraph: { title: t.trending.title, description: t.trending.description, url: "/trending", images: OG_IMAGES },
};

export default async function TrendingPage() {
  const [vi, global, categories] = await Promise.all([
    load(() => getTrending({ max: 36, lang: "vi" })),
    load(() => getTrending({ max: 36 })),
    load(getCategories),
  ]);

  if (!global.ok && global.reason === "missing-keys") {
    return (
      <div className="space-y-6">
        <h1 className="text-2xl font-bold">{t.trending.title}</h1>
        <LoadFailure reason="missing-keys" />
      </div>
    );
  }

  const list = global.ok ? global.data : [];
  return (
    <div className="space-y-10">
      <JsonLd
        data={{
          "@context": "https://schema.org",
          "@type": "ItemList",
          name: t.trending.title,
          itemListElement: list.slice(0, 20).map((p, i) => ({
            "@type": "ListItem",
            position: i + 1,
            url: absoluteUrl(podcastPath(p)),
            name: p.title,
          })),
        }}
      />
      <header>
        <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">{t.trending.title}</h1>
        <p className="mt-1 text-sm text-subtle">{t.trending.description}</p>
      </header>

      <section aria-labelledby="trend-vi">
        <SectionHeader id="trend-vi" title={t.trending.vietnam} />
        {vi.ok ? (
          vi.data.length ? <PodcastGrid podcasts={vi.data} /> : <EmptyState message={t.category.empty} />
        ) : (
          <LoadFailure reason={vi.reason} />
        )}
      </section>

      <section aria-labelledby="trend-global">
        <SectionHeader id="trend-global" title={t.trending.global} />
        {global.ok ? <PodcastGrid podcasts={global.data} /> : <LoadFailure reason={global.reason} />}
      </section>

      {categories.ok && categories.data.length > 0 && (
        <section aria-labelledby="trend-cats">
          <SectionHeader id="trend-cats" title={t.trending.byCategory} />
          <CategoryChips names={categories.data.map((c) => c.name)} />
        </section>
      )}
    </div>
  );
}
