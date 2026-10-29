import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Breadcrumbs } from "@/components/Breadcrumbs";
import { CategoryChips, PodcastGrid } from "@/components/cards";
import { JsonLd } from "@/components/JsonLd";
import { breadcrumbJsonLd } from "@/lib/seo";
import { EmptyState, LoadFailure } from "@/components/notices";
import { categoryLabel, t } from "@/lib/i18n";
import { getCategories, getTrending, load } from "@/lib/podcastindex";
import { OG_IMAGES, STATIC_EXPORT } from "@/lib/site";
import { STATIC_CATEGORY_MAX, staticCategoryParams, visibleCategoryNames } from "@/lib/static-export";
import { slugify } from "@/lib/slug";

export const revalidate = 900;

export function generateStaticParams() {
  // Server build: rendered on first request, then cached (ISR); needs no keys at build time.
  return staticCategoryParams();
}

async function findCategory(slug: string) {
  const res = await load(getCategories);
  if (!res.ok) return { res, category: null };
  return { res, category: res.data.find((c) => slugify(c.name) === slug) ?? null };
}

export async function generateMetadata({ params }: PageProps<"/category/[slug]">): Promise<Metadata> {
  const { slug } = await params;
  const { category } = await findCategory(slug);
  if (!category) return { title: t.nav.trending, robots: { index: false } };
  const label = categoryLabel(category.name);
  const path = `/category/${slugify(category.name)}`;
  return {
    title: t.category.title(label),
    description: t.category.description(label),
    alternates: { canonical: path },
    openGraph: { title: t.category.title(label), description: t.category.description(label), url: path, images: OG_IMAGES },
  };
}

export default async function CategoryPage({ params }: PageProps<"/category/[slug]">) {
  const { slug } = await params;
  const { res, category } = await findCategory(slug);
  if (!res.ok) {
    return (
      <div className="space-y-6">
        <h1 className="text-2xl font-bold">{t.trending.byCategory}</h1>
        <LoadFailure reason={res.reason} />
      </div>
    );
  }
  if (!category) notFound();

  const podcasts = await load(() => getTrending({ max: STATIC_EXPORT ? STATIC_CATEGORY_MAX : 48, cat: category.name }));
  const label = categoryLabel(category.name);
  const crumbs = [
    { name: t.nav.home, path: "/" },
    { name: t.nav.trending, path: "/trending" },
    { name: label, path: `/category/${slug}` },
  ];
  return (
    <div className="space-y-8">
      <JsonLd data={breadcrumbJsonLd(crumbs)} />
      <Breadcrumbs items={crumbs} />
      <header>
        <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">{t.category.title(label)}</h1>
        <p className="mt-1 text-sm text-subtle">{t.category.description(label)}</p>
      </header>
      {podcasts.ok ? (
        podcasts.data.length ? <PodcastGrid podcasts={podcasts.data} /> : <EmptyState message={t.category.empty} />
      ) : (
        <LoadFailure reason={podcasts.reason} />
      )}
      <section aria-labelledby="all-cats">
        <h2 id="all-cats" className="mb-3 text-lg font-bold">
          {t.category.all}
        </h2>
        <CategoryChips names={visibleCategoryNames(res.data.map((c) => c.name))} current={category.name} />
      </section>
    </div>
  );
}
