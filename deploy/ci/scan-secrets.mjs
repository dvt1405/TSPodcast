// Scan a built web image (its /app copied out with `docker cp`) for secrets.
// Same idea as web/scripts/build-static.mjs, which scans out/ of the static site.
//
//   node deploy/ci/scan-secrets.mjs <dir> [<dir>...]
//
// 1. Values: the current values of PODCASTINDEX_API_KEY, PODCASTINDEX_API_SECRET
//    and ANTHROPIC_API_KEY, taken from the environment (GitHub secrets of the
//    `production` environment). The image is built without them, so a hit means
//    a key was hard-coded somewhere in web/. Names without a value are skipped.
// 2. Patterns: anything shaped like an Anthropic key or a private key block.
// 3. Client bundle: no `process.env.<secret name>` in .next/static (server code
//    bundled for the browser). The bare names do appear there on purpose, in
//    the UI text that says which variable to set.
//
// Prints file paths and secret *names*, never values. Exit 1 on any finding.
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative, sep } from "node:path";

const dirs = process.argv.slice(2);
if (dirs.length === 0) {
  console.error("usage: node deploy/ci/scan-secrets.mjs <dir> [<dir>...]");
  process.exit(2);
}

const SECRET_NAMES = ["PODCASTINDEX_API_KEY", "PODCASTINDEX_API_SECRET", "ANTHROPIC_API_KEY"];
const values = SECRET_NAMES.map((name) => ({ name, value: (process.env[name] ?? "").trim() })).filter(
  (s) => s.value.length >= 8,
);
const PATTERNS = [
  { name: "Anthropic API key", re: /sk-ant-[A-Za-z0-9_-]{20,}/ },
  { name: "private key block", re: /-----BEGIN [A-Z ]*PRIVATE KEY-----/ },
];
const CLIENT_ENV_READ = new RegExp(`process\\.env(?:\\.|\\[["'])(${SECRET_NAMES.join("|")})\\b`);
// Third-party packages ship test fixtures and docs; patterns there are noise.
// Secret values are still searched everywhere.
const PATTERN_SKIP = `${sep}node_modules${sep}`;

function* files(dir) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    const st = statSync(path, { throwIfNoEntry: false });
    if (!st) continue;
    if (st.isDirectory()) yield* files(path);
    else if (st.isFile()) yield path;
  }
}

const findings = [];
let count = 0;
for (const dir of dirs) {
  for (const file of files(dir)) {
    count++;
    const text = readFileSync(file).toString("latin1");
    const rel = relative(process.cwd(), file);
    for (const s of values) if (text.includes(s.value)) findings.push(`value of ${s.name} in ${rel}`);
    if (!file.includes(PATTERN_SKIP)) {
      for (const p of PATTERNS) if (p.re.test(text)) findings.push(`${p.name} in ${rel}`);
    }
    if (file.includes(`${sep}.next${sep}static${sep}`)) {
      const m = CLIENT_ENV_READ.exec(text);
      if (m) findings.push(`process.env.${m[1]} read in client bundle ${rel}`);
    }
  }
}

if (count === 0) {
  console.error(`No files under ${dirs.join(", ")}: nothing was scanned.`);
  process.exit(1);
}
if (findings.length) {
  console.error(`Secret scan FAILED (${findings.length}):\n  ${findings.join("\n  ")}`);
  console.error("Do not deploy this image. Remove the secret from the code and rotate the key.");
  process.exit(1);
}
const checked = values.map((s) => s.name).join(", ") || "none (no secret values in the environment)";
console.log(`Secret scan ok: ${count} files; values checked: ${checked}; patterns: ${PATTERNS.map((p) => p.name).join(", ")}.`);
