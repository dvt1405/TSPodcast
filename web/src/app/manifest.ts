import type { MetadataRoute } from "next";
import { t } from "@/lib/i18n";
import { ANDROID_PACKAGE, PLAY_STORE_URL, SITE_NAME } from "@/lib/site";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: SITE_NAME,
    short_name: SITE_NAME,
    description: t.meta.description,
    start_url: "/",
    display: "standalone",
    background_color: "#f6f7f8",
    theme_color: "#184bb4",
    icons: [{ src: "/icon.svg", sizes: "any", type: "image/svg+xml" }],
    related_applications: [{ platform: "play", url: PLAY_STORE_URL, id: ANDROID_PACKAGE }],
    prefer_related_applications: false,
  };
}
