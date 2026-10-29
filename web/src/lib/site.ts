// Public, non-secret site configuration. Safe to import from client components.

export const SITE_NAME = "TSPodcast";

/**
 * Static export build (`npm run build:static`): pages are rendered once at build
 * time for a plain static host, so there is no server for search or AI. Those
 * routes are left out of the build and their links are hidden.
 */
export const STATIC_EXPORT = process.env.NEXT_PUBLIC_STATIC_EXPORT === "1";

/** Canonical origin, no trailing slash. Set NEXT_PUBLIC_SITE_URL in production. */
export const SITE_URL = (
  process.env.NEXT_PUBLIC_SITE_URL || "http://localhost:3000"
).replace(/\/+$/, "");

/** Same applicationId as the Android app (app/build.gradle.kts). */
export const ANDROID_PACKAGE = "tss.t.podcast";

export const PLAY_STORE_URL = `https://play.google.com/store/apps/details?id=${ANDROID_PACKAGE}`;

/**
 * Play Store link carrying an install referrer (docs/plans/seo-web-dashboard.md
 * §A.10): utm_* for attribution plus `deeplink=/podcast/<id>` so the app can
 * route there after install via the Play Install Referrer API.
 */
export function playStoreUrl({
  medium = "website",
  content,
  deeplink,
}: { medium?: string; content?: string; deeplink?: string } = {}): string {
  const referrer = new URLSearchParams({ utm_source: "web", utm_medium: medium, utm_campaign: "seo" });
  if (content) referrer.set("utm_content", content);
  if (deeplink) referrer.set("deeplink", deeplink);
  return `${PLAY_STORE_URL}&referrer=${encodeURIComponent(referrer.toString())}`;
}

/**
 * Opens the installed app's launcher activity, falling back to the Play Store.
 * The app declares no VIEW/BROWSABLE intent filter yet (app/src/main/
 * AndroidManifest.xml; App Links are planned in §A.10), so this launches the
 * app's home screen rather than the podcast being viewed.
 */
export function androidIntentUrl(fallback: string = playStoreUrl()): string {
  return (
    `intent://#Intent;action=android.intent.action.MAIN;` +
    `category=android.intent.category.LAUNCHER;package=${ANDROID_PACKAGE};` +
    `S.browser_fallback_url=${encodeURIComponent(fallback)};end`
  );
}

export const PODCASTINDEX_URL = "https://podcastindex.org";

/** Default share image (brand/web/og-image.png). Next does not merge openGraph, so pages reuse this. */
export const OG_IMAGES = [{ url: "/og-image.png", width: 1200, height: 630, alt: SITE_NAME }];

export function absoluteUrl(path: string): string {
  return `${SITE_URL}${path.startsWith("/") ? path : `/${path}`}`;
}
