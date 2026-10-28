import { ImageResponse } from "next/og";
import { t } from "@/lib/i18n";
import { SITE_NAME } from "@/lib/site";

export const alt = SITE_NAME;
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

// Site-wide default share image. Podcast/episode pages override og:image with
// their own artwork.
export default function OpengraphImage() {
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          flexDirection: "column",
          justifyContent: "center",
          padding: 80,
          background: "linear-gradient(135deg, #184BB4 0%, #4A7DE7 100%)",
          color: "white",
        }}
      >
        <div style={{ fontSize: 96, fontWeight: 700, letterSpacing: -2 }}>{SITE_NAME}</div>
        <div style={{ fontSize: 40, marginTop: 24, opacity: 0.92, maxWidth: 980 }}>{t.meta.tagline}</div>
      </div>
    ),
    size,
  );
}
