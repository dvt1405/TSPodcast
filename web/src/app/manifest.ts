import type { MetadataRoute } from "next";
import { htmlLang, t } from "@/lib/i18n";
import { ANDROID_PACKAGE, PLAY_STORE_URL, SITE_NAME } from "@/lib/site";

// Mirrors brand/web/site.webmanifest (icons copied into public/), plus the
// Play listing as a related application.
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: SITE_NAME,
    short_name: SITE_NAME,
    description: t.meta.description,
    lang: htmlLang,
    start_url: "/",
    display: "standalone",
    background_color: "#FFFFFF",
    theme_color: "#184BB4",
    icons: [
      { src: "/icon-192.png", sizes: "192x192", type: "image/png" },
      { src: "/icon-512.png", sizes: "512x512", type: "image/png" },
      { src: "/icon-maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
    related_applications: [{ platform: "play", url: PLAY_STORE_URL, id: ANDROID_PACKAGE }],
    prefer_related_applications: false,
  };
}
