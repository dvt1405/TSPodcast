// Generates store, web and social artwork:
//   brand/store/play-feature-graphic-1024x500.png
//   brand/store/appstore-icon-1024.png          (no alpha)
//   brand/store/play-icon-512.png
//   brand/store/og-image-1200x630.png (+ .svg)
//   brand/web/  favicon.svg, favicon.ico, favicon-16/32.png, apple-touch-icon.png,
//               icon-192.png, icon-512.png, icon-maskable-512.png, site.webmanifest
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('./lib');
const { tile } = require('./logo');
const { PALETTE: P } = L;

const STORE = path.join(L.BRAND, 'store');
const WEB = path.join(L.BRAND, 'web');
const TAGLINE = 'Thế giới trong tai bạn';
const DESCRIPTOR = 'Podcast · Radio · Nhạc';

const nightGradient = (id) => `<linearGradient id="${id}" x1="0" y1="0" x2="1" y2="1">` +
  `<stop offset="0" stop-color="${P.blue}"/><stop offset="1" stop-color="${P.night}"/></linearGradient>`;
const glow = (id, color, op = 0.55) => `<radialGradient id="${id}"><stop offset="0" stop-color="${color}" stop-opacity="${op}"/>` +
  `<stop offset="1" stop-color="${color}" stop-opacity="0"/></radialGradient>`;

/** Inline lockup: tile + wordmark, left-aligned at (x, top) with tile height T. */
function lockupInline(x, top, T, { dark = true } = {}) {
  const size = T * 0.7;
  const capH = L.capHeight(size);
  return tile(x, top, T) +
    L.wordmark(x + T * 1.27, top + T / 2 + capH / 2, size,
      dark ? { ts: '#8FB0FF', rest: P.white } : { ts: P.blue, rest: P.ink });
}

// ---------------------------------------------------------------- feature graphic
function featureGraphic() {
  const W = 1024; const H = 500;
  const defs = L.gradientDef('tsg') + nightGradient('bg') + glow('glow', P.bright) + glow('coralGlow', P.coral, 0.35);
  let b = `<rect width="${W}" height="${H}" fill="url(#bg)"/>`;
  b += `<circle cx="830" cy="150" r="380" fill="url(#glow)"/>`;
  b += L.waveform(560, 250, 430, 300, { n: 22, opacity: 0.16 });
  // big outline mark on the right as a graphic device
  b += L.markCentered(640, 90, 320, 0.95, { line: 'rgba(255,255,255,0.92)', dot: P.coral });
  // copy block, left
  b += lockupInline(64, 120, 96);
  b += L.text(TAGLINE, { x: 66, y: 320, size: 46, weight: 800, fill: P.white, tracking: -10 });
  b += L.text(DESCRIPTOR, { x: 68, y: 372, size: 26, weight: 500, fill: '#C9D6FF' });
  return L.svgDoc(W, H, b, { defs, title: 'TSPodcast — Google Play feature graphic' });
}

// ---------------------------------------------------------------- OG / social
function ogImage() {
  const W = 1200; const H = 630;
  const defs = L.gradientDef('tsg') + glow('glow', P.bright, 0.28);
  let b = `<rect width="${W}" height="${H}" fill="${P.mist}"/>`;
  b += `<circle cx="1000" cy="320" r="420" fill="url(#glow)"/>`;
  b += L.waveform(700, 330, 440, 380, { n: 22, fill: P.blue, opacity: 0.12, seed: 1.3 });
  b += tile(790, 165, 300, { fill: 0.62 });
  b += L.wordmark(80, 250, 92, { ts: P.blue, rest: P.ink });
  b += L.text(TAGLINE, { x: 82, y: 350, size: 52, weight: 800, fill: P.ink, tracking: -10 });
  b += L.text('Nghe podcast, radio và nhạc', { x: 84, y: 418, size: 32, weight: 500, fill: P.slate });
  b += L.text('miễn phí trên Android.', { x: 84, y: 462, size: 32, weight: 500, fill: P.slate });
  b += `<circle cx="96" cy="538" r="10" fill="${P.coral}"/>`;
  b += L.text("Có trên Google Play", { x: 118, y: 549, size: 30, weight: 700, fill: P.blue });
  return L.svgDoc(W, H, b, { defs, title: 'TSPodcast — social preview' });
}

// ---------------------------------------------------------------- icons
const iconSquare = (size, fill = 0.62) => L.svgDoc(size, size, tile(0, 0, size, { rounded: false, fill }), { defs: L.gradientDef('tsg') });
const iconRounded = (size, fill = 0.62) => L.svgDoc(size, size, tile(0, 0, size, { rounded: true, fill }), { defs: L.gradientDef('tsg') });
// Small favicons: rounder tile, mark scaled up a bit (more legible at 16/32 px).
const iconSmall = (size) => L.svgDoc(size, size, `<rect width="${size}" height="${size}" rx="${size * 0.28}" fill="url(#tsg)"/>` +
  L.markCentered(0, 0, size, 0.72, { line: P.white, dot: P.coral }), { defs: L.gradientDef('tsg') });

/** Minimal .ico writer that embeds PNG images (supported since Windows Vista / all browsers). */
function ico(pngs) {
  const header = Buffer.alloc(6); header.writeUInt16LE(0, 0); header.writeUInt16LE(1, 2); header.writeUInt16LE(pngs.length, 4);
  const dir = Buffer.alloc(16 * pngs.length);
  let offset = 6 + dir.length;
  pngs.forEach(({ size, buf }, i) => {
    const o = i * 16;
    dir.writeUInt8(size >= 256 ? 0 : size, o); dir.writeUInt8(size >= 256 ? 0 : size, o + 1);
    dir.writeUInt8(0, o + 2); dir.writeUInt8(0, o + 3); dir.writeUInt16LE(1, o + 4); dir.writeUInt16LE(32, o + 6);
    dir.writeUInt32LE(buf.length, o + 8); dir.writeUInt32LE(offset, o + 12);
    offset += buf.length;
  });
  return Buffer.concat([header, dir, ...pngs.map((p) => p.buf)]);
}

const WEBMANIFEST = {
  name: 'TSPodcast',
  short_name: 'TSPodcast',
  description: 'Nghe podcast, radio và nhạc — Thế giới trong tai bạn.',
  lang: 'vi',
  start_url: '/',
  display: 'standalone',
  background_color: P.white,
  theme_color: P.blue,
  icons: [
    { src: '/icon-192.png', sizes: '192x192', type: 'image/png' },
    { src: '/icon-512.png', sizes: '512x512', type: 'image/png' },
    { src: '/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
  ],
};

async function main() {
  // store
  const fg = featureGraphic();
  L.writeText(path.join(STORE, 'src', 'play-feature-graphic.svg'), fg);
  await L.render(fg, path.join(STORE, 'play-feature-graphic-1024x500.png'), 1024, { flatten: P.night });
  await L.render(iconSquare(1024), path.join(STORE, 'appstore-icon-1024.png'), 1024, { flatten: P.blue });
  await L.render(iconSquare(512), path.join(STORE, 'play-icon-512.png'), 512);
  const og = ogImage();
  L.writeText(path.join(STORE, 'src', 'og-image.svg'), og);
  await L.render(og, path.join(STORE, 'og-image-1200x630.png'), 1200, { flatten: P.mist });

  // web
  L.writeText(path.join(WEB, 'favicon.svg'), iconSmall(64).replace('<title>TSPodcast</title>', '<title>TSPodcast</title>'));
  const small = {};
  for (const s of [16, 32, 48]) {
    const p = path.join(WEB, `favicon-${s}.png`);
    await L.render(iconSmall(s), p, s);
    small[s] = fs.readFileSync(p);
  }
  fs.rmSync(path.join(WEB, 'favicon-48.png')); // only needed inside the .ico
  fs.writeFileSync(path.join(WEB, 'favicon.ico'), ico([16, 32, 48].map((size) => ({ size, buf: small[size] }))));
  // apple-touch: full bleed, opaque (iOS applies its own rounding and shows black under alpha)
  await L.render(iconSquare(180), path.join(WEB, 'apple-touch-icon.png'), 180, { flatten: P.blue });
  await L.render(iconRounded(192), path.join(WEB, 'icon-192.png'), 192);
  await L.render(iconRounded(512), path.join(WEB, 'icon-512.png'), 512);
  // maskable: content inside the central 80% safe circle
  await L.render(iconSquare(512, 0.5), path.join(WEB, 'icon-maskable-512.png'), 512, { flatten: P.blue });
  L.writeText(path.join(WEB, 'site.webmanifest'), `${JSON.stringify(WEBMANIFEST, null, 2)}\n`);
  fs.copyFileSync(path.join(STORE, 'og-image-1200x630.png'), path.join(WEB, 'og-image.png'));
  console.log('store + web: done');
}

module.exports = { main, lockupInline, TAGLINE, DESCRIPTOR, nightGradient, glow };
if (require.main === module) main().catch((e) => { console.error(e); process.exit(1); });
