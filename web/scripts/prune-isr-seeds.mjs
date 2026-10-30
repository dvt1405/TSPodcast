// Docker build only (web/Dockerfile): `node scripts/prune-isr-seeds.mjs <distDir>`.
//
// The image is built on CI without PODCASTINDEX_* / ANTHROPIC_API_KEY and
// without the runtime-only settings (APP_ADS_TXT, ANDROID_CERT_SHA256,
// GOOGLE_SITE_VERIFICATION). Every ISR route prerendered during that build
// therefore holds a "keys not configured" page, an empty sitemap or a 404
// app-ads.txt, and Next would serve that copy until its revalidate period ran
// out (up to 6 h for /radio) after every deploy.
//
// This deletes the build-time copies of routes that have a revalidate period,
// so the first request after a deploy renders them with the server's env (a
// cache MISS, blocking) and caches the result as usual. Routes without a
// revalidate period (robots.txt, manifest, _not-found, _global-error) are
// kept: they only depend on build-time values.
import { existsSync, readFileSync, rmSync } from "node:fs";
import { join } from "node:path";

const distDir = process.argv[2];
if (!distDir) {
  console.error("usage: node scripts/prune-isr-seeds.mjs <path to .next>");
  process.exit(2);
}

const manifest = JSON.parse(readFileSync(join(distDir, "prerender-manifest.json"), "utf8"));
const appDir = join(distDir, "server", "app");

const pruned = [];
for (const [route, info] of Object.entries(manifest.routes ?? {})) {
  if (info.initialRevalidateSeconds === false) continue;
  const base = join(appDir, route === "/" ? "index" : route.replace(/^\//, ""));
  // Pages: .html/.rsc/.meta/.segments. Route handlers: .body/.meta.
  const candidates = [".html", ".rsc", ".meta", ".segments", ".body"].map((ext) => base + ext);
  const removed = candidates.filter((path) => existsSync(path));
  for (const path of removed) rmSync(path, { recursive: true, force: true });
  if (removed.length) pruned.push(route);
}

console.log(`prune-isr-seeds: removed build-time copies of ${pruned.length} ISR route(s): ${pruned.join(", ") || "none"}`);
