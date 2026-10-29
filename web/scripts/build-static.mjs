// Static export for Firebase Hosting: `npm run build:static` → out/.
// Pages are rendered once with the PodcastIndex keys from .env.local (or the
// environment); the keys must never reach out/, so the result is scanned for
// them and the build fails if any secret value shows up.
import { spawnSync } from "node:child_process";
import { readdirSync, readFileSync, rmSync, statSync } from "node:fs";
import { join } from "node:path";
import nextEnv from "@next/env";

const root = new URL("..", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");
const { combinedEnv } = nextEnv.loadEnvConfig(root, false);

const env = { ...process.env, NEXT_PUBLIC_STATIC_EXPORT: "1" };
// No server for /api/ai/*: keep the AI key out of the build entirely.
delete env.ANTHROPIC_API_KEY;

rmSync(join(root, "out"), { recursive: true, force: true });
const build = spawnSync("npx next build", { cwd: root, env, stdio: "inherit", shell: true });
if (build.status !== 0) process.exit(build.status ?? 1);

const secrets = ["PODCASTINDEX_API_KEY", "PODCASTINDEX_API_SECRET", "ANTHROPIC_API_KEY"]
  .map((name) => ({ name, value: (process.env[name] ?? combinedEnv[name] ?? "").trim() }))
  .filter((s) => s.value.length >= 8);

function* files(dir) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) yield* files(path);
    else yield path;
  }
}

// t.errors.loadFailed (vi, en): a page that hit an API error at build time
// keeps that error until the next build.
const FAILURE_MARKERS = ["Không tải được dữ liệu", "Could not load data"];

let count = 0;
let pages = 0;
const leaks = [];
const failed = [];
for (const file of files(join(root, "out"))) {
  count++;
  const buf = readFileSync(file);
  const text = buf.toString("latin1");
  for (const s of secrets) if (text.includes(s.value)) leaks.push(`${s.name} in ${file}`);
  if (file.endsWith(".html")) {
    pages++;
    const html = buf.toString("utf8");
    if (FAILURE_MARKERS.some((m) => html.includes(m))) failed.push(file);
  }
}
if (leaks.length) {
  console.error(`\nSecret values found in out/ — do not deploy:\n  ${leaks.join("\n  ")}`);
  rmSync(join(root, "out"), { recursive: true, force: true });
  process.exit(1);
}
if (failed.length) {
  console.warn(`\n${failed.length}/${pages} pages were built with a load error, e.g.:\n  ${failed.slice(0, 5).join("\n  ")}`);
  if (failed.length > pages * 0.02) {
    console.error("More than 2% of pages failed to load data (PodcastIndex rate limit?). Not deploying this build.");
    process.exit(1);
  }
}
console.log(`\nout/: ${count} files, ${pages} pages, no secret values found (checked ${secrets.map((s) => s.name).join(", ") || "none"}).`);
