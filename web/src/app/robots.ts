import type { MetadataRoute } from "next";
import { SITE_URL } from "@/lib/site";

// Built once (also required by the static export build).
export const dynamic = "force-static";

export default function robots(): MetadataRoute.Robots {
  // Non-production deployments (previews) must not be indexed.
  if (process.env.SITE_NOINDEX === "1") {
    return { rules: { userAgent: "*", disallow: "/" } };
  }
  return {
    rules: { userAgent: "*", allow: "/", disallow: ["/search", "/api/"] },
    sitemap: `${SITE_URL}/sitemap.xml`,
    host: SITE_URL,
  };
}
