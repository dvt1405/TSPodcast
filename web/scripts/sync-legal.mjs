// Copies the legal pages (privacy, terms, delete-data in vi/en) from the product site sources
// (../site/_src/content, the single source of truth) into src/legal/, so the web app and its Docker
// build (context = web/) serve the same text at /privacy, /terms, /delete-data and /en/*.
//
//   node scripts/sync-legal.mjs        (run after editing site/_src/content/*.html)
import { copyFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const src = join(here, "../../site/_src/content");
const out = join(here, "../src/legal");
const files = readdirSync(src).filter((f) => /^(privacy|terms|delete-data)\.(vi|en)\.html$/.test(f));
for (const f of files) copyFileSync(join(src, f), join(out, f));
console.log(`synced ${files.length} legal pages into src/legal/`);
