import type { NextConfig } from "next";

// Cache Components is deliberately left off. Its strict static-shell model
// needs generateStaticParams to return real ids at build time, which needs
// PodcastIndex keys during the build. The classic model below (route
// `revalidate` + unstable_cache in src/lib/podcastindex.ts) gives blocking ISR:
// a crawler's first hit on /podcast/123-x gets full HTML, not a fallback shell.

// `npm run build:static` sets this: plain HTML in out/ for a static host
// (Firebase Hosting). Routes that need a server (search, /api/ai/*) are named
// *.dynamic.ts(x) and only picked up by the server build; security headers for
// the static site live in ../firebase.json.
const staticExport = process.env.NEXT_PUBLIC_STATIC_EXPORT === "1";

const SECURITY_HEADERS = [
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "X-Frame-Options", value: "SAMEORIGIN" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
];

const nextConfig: NextConfig = {
  poweredByHeader: false,
  output: staticExport ? "export" : undefined,
  pageExtensions: staticExport ? ["tsx", "ts"] : ["tsx", "ts", "dynamic.tsx", "dynamic.ts"],
  // Fewer build workers in a static export: together with the per-worker
  // spacing in src/lib/podcastindex.ts this keeps PodcastIndex below its 429 limit.
  ...(staticExport ? { experimental: { cpus: 2 } } : {}),
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  ...(staticExport
    ? {}
    : {
        async headers() {
          return [{ source: "/:path*", headers: SECURITY_HEADERS }];
        },
      }),
};

export default nextConfig;
