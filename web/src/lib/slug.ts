/** ASCII slug that handles Vietnamese diacritics (đ is not decomposed by NFD). */
export function slugify(input: string | null | undefined, maxLength = 80): string {
  const slug = (input ?? "")
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, maxLength)
    .replace(/-+$/g, "");
  return slug || "podcast";
}

/** Parses the `123-some-title` segment used by /podcast and /episode. */
export function parseIdSlug(segment: string): { id: number; slug: string } | null {
  const match = /^(\d{1,15})(?:-(.*))?$/.exec(decodeURIComponent(segment));
  if (!match) return null;
  const id = Number(match[1]);
  if (!Number.isSafeInteger(id) || id <= 0) return null;
  return { id, slug: match[2] ?? "" };
}

export function podcastPath(p: { id: number; title?: string | null }): string {
  return `/podcast/${p.id}-${slugify(p.title)}`;
}

export function episodePath(e: { id: number; title?: string | null }): string {
  return `/episode/${e.id}-${slugify(e.title)}`;
}

export function categoryPath(name: string): string {
  return `/category/${slugify(name)}`;
}
