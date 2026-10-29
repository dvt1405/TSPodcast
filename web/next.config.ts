import type { NextConfig } from "next";

// Cache Components is deliberately left off. Its strict static-shell model
// needs generateStaticParams to return real ids at build time, which needs
// PodcastIndex keys during the build. The classic model below (route
// `revalidate` + unstable_cache in src/lib/podcastindex.ts) gives blocking ISR:
// a crawler's first hit on /podcast/123-x gets full HTML, not a fallback shell.
const nextConfig: NextConfig = {
  poweredByHeader: false,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "X-Frame-Options", value: "SAMEORIGIN" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
        ],
      },
    ];
  },
};

export default nextConfig;
