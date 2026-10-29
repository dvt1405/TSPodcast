// Downloads Be Vietnam Pro (SIL Open Font License 1.1) static TTFs from the Google Fonts API
// into brand/scripts/fonts/. The fonts are not committed; build.js calls this when they are missing.
'use strict';
const fs = require('fs');
const path = require('path');

const WEIGHTS = [400, 500, 600, 700, 800];
const DIR = path.join(__dirname, 'fonts');

async function fetchFonts({ force = false } = {}) {
  const missing = WEIGHTS.filter((w) => force || !fs.existsSync(path.join(DIR, `BeVietnamPro-${w}.ttf`)));
  if (!missing.length) return;
  fs.mkdirSync(DIR, { recursive: true });
  // No browser User-Agent -> the CSS API answers with full (unsubset) TrueType files.
  const css = await (await fetch(`https://fonts.googleapis.com/css2?family=Be+Vietnam+Pro:wght@${WEIGHTS.join(';')}`)).text();
  const faces = [...css.matchAll(/font-weight:\s*(\d+);[\s\S]*?url\((https:[^)]+\.ttf)\)/g)];
  for (const [, w, url] of faces) {
    if (!missing.includes(+w)) continue;
    const buf = Buffer.from(await (await fetch(url)).arrayBuffer());
    fs.writeFileSync(path.join(DIR, `BeVietnamPro-${w}.ttf`), buf);
    console.log(`font: Be Vietnam Pro ${w} (${buf.length} bytes)`);
  }
}

module.exports = { fetchFonts };
if (require.main === module) fetchFonts({ force: process.argv.includes('--force') }).catch((e) => { console.error(e); process.exit(1); });
