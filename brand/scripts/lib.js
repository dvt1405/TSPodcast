// Shared helpers for the TSPodcast brand generators.
// - PALETTE: the single source of truth for brand colours (mirrored in brand/BRAND.md
//   and app/src/main/res/values/colors.xml).
// - mark(): the "Sóng TS" monogram geometry (hand-written, geometric: one line + two arcs).
// - text(): Be Vietnam Pro text converted to SVG paths with opentype.js, so every exported
//   SVG is font-independent and Vietnamese diacritics always render.
// - render(): SVG string -> PNG via resvg (optionally flattened / resized with sharp).
'use strict';
const fs = require('fs');
const path = require('path');
const opentype = require('opentype.js');
const { Resvg } = require('@resvg/resvg-js');
const sharp = require('sharp');

const ROOT = path.resolve(__dirname, '..', '..');
const BRAND = path.join(ROOT, 'brand');

const PALETTE = {
  blue: '#184BB4', // Xanh TS — primary (same as in-app ColorsTheme.Primary)
  bright: '#3D7BFF', // Xanh Sáng — gradient start, highlights
  night: '#0B1A4A', // Xanh Đêm — dark surfaces
  coral: '#FF7A59', // Cam San Hô — accent (live dot, CTAs)
  ink: '#141B34', // Mực — text on light
  mist: '#EEF3FF', // Sương — light surfaces
  slate: '#7E858C', // Xám — secondary text (same as in-app TextDescriptionColor)
  white: '#FFFFFF',
};

// ---------------------------------------------------------------------------
// The mark — concept "Lên sóng" (On air). Designed on a 100×100 grid.
//   T    : crossbar + stem. The crossbar runs on into the S: one line, two letters.
//   S    : two half-circles of one stroke weight = a sound wave, ending in a short tail.
//   dot  : the coral "on air" lamp at the end of the bar — live radio / recording.
// Stroke-based (14u, round caps) so it maps 1:1 onto Android vector drawables.
// ---------------------------------------------------------------------------
const MARK = {
  stroke: 14,
  t: 'M14,24 H70 M31,24 V84',
  s: 'M66,24 A14,14 0 0 0 66,52 A16,16 0 0 1 66,84 H54',
  dot: { cx: 89, cy: 24, r: 7.5 },
  // inked bounding box on the 100 grid (stroke and dot included)
  bbox: { x: 7, y: 16.5, w: 89.5, h: 74.5 },
};
MARK.bar = MARK.t; // back-compat aliases
MARK.wave = MARK.s;

/**
 * Returns SVG markup of the mark, mapped so that the 100-grid box lands at (x,y) with size s.
 * colors: { line, dot } — pass the same colour twice for monochrome.
 */
function mark(x, y, s, colors) {
  const k = s / 100;
  const { line, dot } = colors;
  return `<g transform="translate(${r(x)} ${r(y)}) scale(${r(k, 5)})">` +
    `<path d="${MARK.t} ${MARK.s}" ` +
    `fill="none" stroke="${line}" stroke-width="${MARK.stroke}" stroke-linecap="round" stroke-linejoin="round"/>` +
    `<circle cx="${MARK.dot.cx}" cy="${MARK.dot.cy}" r="${MARK.dot.r}" fill="${dot}"/>` +
    `</g>`;
}

/** Place the mark optically centred in a square of side `size` at (x,y), occupying `fill` of it. */
function markCentered(x, y, size, fill, colors) {
  const b = MARK.bbox;
  const s = (size * fill) / Math.max(b.w, b.h) * 100; // grid units -> px
  const k = s / 100;
  const ox = x + size / 2 - (b.x + b.w / 2) * k;
  const oy = y + size / 2 - (b.y + b.h / 2) * k;
  return mark(ox, oy, s, colors);
}

// ---------------------------------------------------------------------------
// Typography
// ---------------------------------------------------------------------------
const fontCache = {};
function font(weight) {
  if (!fontCache[weight]) {
    const b = fs.readFileSync(path.join(__dirname, 'fonts', `BeVietnamPro-${weight}.ttf`));
    fontCache[weight] = opentype.parse(b.buffer.slice(b.byteOffset, b.byteOffset + b.length));
  }
  return fontCache[weight];
}

/** Advance width of a string in px. tracking is in em/1000 (like CSS letter-spacing * 1000). */
function measure(str, { weight = 700, size = 32, tracking = 0 } = {}) {
  const f = font(weight);
  const glyphs = f.stringToGlyphs(str);
  let w = 0;
  glyphs.forEach((g, i) => {
    w += g.advanceWidth;
    if (i < glyphs.length - 1) w += f.getKerningValue(g, glyphs[i + 1]) + tracking;
  });
  return (w * size) / f.unitsPerEm;
}

// opentype.js 2.0's Path.toPathData() rounding emits "NaN" for some coordinates, so serialise here.
function pathData(cmds) {
  const n = (v) => (Math.round(v * 100) / 100).toString();
  return cmds.map((c) => {
    switch (c.type) {
      case 'M': case 'L': return `${c.type}${n(c.x)} ${n(c.y)}`;
      case 'Q': return `Q${n(c.x1)} ${n(c.y1)} ${n(c.x)} ${n(c.y)}`;
      case 'C': return `C${n(c.x1)} ${n(c.y1)} ${n(c.x2)} ${n(c.y2)} ${n(c.x)} ${n(c.y)}`;
      case 'Z': return 'Z';
      default: throw new Error(`path command ${c.type}`);
    }
  }).join('');
}

/**
 * Text as an SVG <path>. anchor: 'start' | 'middle' | 'end'. y is the baseline.
 * Segments let one line mix weights/colours: text([{t:'TS',weight:800,fill:..},{t:'Podcast'}], opts)
 */
function text(content, { x = 0, y = 0, size = 32, weight = 700, fill = PALETTE.ink, anchor = 'start', tracking = 0 } = {}) {
  const segs = (Array.isArray(content) ? content : [{ t: content }]).map((s) => ({
    t: s.t, weight: s.weight || weight, fill: s.fill || fill,
  }));
  const widths = segs.map((s) => measure(s.t, { weight: s.weight, size, tracking }));
  const total = widths.reduce((a, b) => a + b, 0) + (segs.length - 1) * (tracking * size) / 1000;
  let cx = anchor === 'middle' ? x - total / 2 : anchor === 'end' ? x - total : x;
  let out = '';
  segs.forEach((s, i) => {
    const f = font(s.weight);
    const glyphs = f.stringToGlyphs(s.t);
    let d = '';
    let gx = cx;
    glyphs.forEach((g, j) => {
      d += pathData(g.getPath(gx, y, size).commands);
      gx += (g.advanceWidth * size) / f.unitsPerEm;
      if (j < glyphs.length - 1) gx += ((f.getKerningValue(g, glyphs[j + 1]) + tracking) * size) / f.unitsPerEm;
    });
    out += `<path d="${d}" fill="${s.fill}"/>`;
    cx += widths[i] + (tracking * size) / 1000;
  });
  return out;
}

/** Cap height / x-height helpers for layout (px at `size`). */
function capHeight(size, weight = 800) {
  const f = font(weight);
  return ((f.tables.os2.sCapHeight || 700) * size) / f.unitsPerEm;
}

// ---------------------------------------------------------------------------
// Wordmark "TSPodcast": TS in 800 brand blue, Podcast in 700 ink.
// ---------------------------------------------------------------------------
function wordmark(x, baseline, size, { ts = PALETTE.blue, rest = PALETTE.ink, anchor = 'start' } = {}) {
  return text([{ t: 'TS', weight: 800, fill: ts }, { t: 'Podcast', weight: 700, fill: rest }],
    { x, y: baseline, size, anchor, tracking: -12 });
}
function wordmarkWidth(size) {
  return measure('TS', { weight: 800, size, tracking: -12 }) + measure('Podcast', { weight: 700, size, tracking: -12 }) + (-12 * size) / 1000;
}

// ---------------------------------------------------------------------------
// Graphic motif: "sóng" — a row of rounded bars whose heights follow a damped wave.
// Used as background texture on store / social / video artwork (never inside the mark).
// ---------------------------------------------------------------------------
function waveform(x, cy, width, maxH, { n = 28, fill = PALETTE.white, opacity = 0.18, seed = 1 } = {}) {
  const step = width / n;
  const bw = step * 0.5;
  let out = `<g fill="${fill}" opacity="${opacity}">`;
  for (let i = 0; i < n; i++) {
    const t = i / (n - 1);
    const env = Math.sin(Math.PI * t); // swell in the middle
    const osc = 0.55 + 0.45 * Math.abs(Math.sin(i * 0.9 * seed + 0.6));
    const h = Math.max(bw, maxH * env * osc);
    out += `<rect x="${r(x + i * step + (step - bw) / 2)}" y="${r(cy - h / 2)}" width="${r(bw)}" height="${r(h)}" rx="${r(bw / 2)}"/>`;
  }
  return out + '</g>';
}

/** Greedy word-wrap for outlined text. Returns lines. */
function wrap(str, maxWidth, opts) {
  const words = str.split(/\s+/);
  const lines = [];
  let cur = '';
  for (const w of words) {
    const next = cur ? `${cur} ${w}` : w;
    if (cur && measure(next, opts) > maxWidth) { lines.push(cur); cur = w; } else cur = next;
  }
  if (cur) lines.push(cur);
  return lines;
}

// ---------------------------------------------------------------------------
// SVG + raster
// ---------------------------------------------------------------------------
function svgDoc(w, h, body, { defs = '', title = 'TSPodcast' } = {}) {
  return `<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">` +
    `<title>${title}</title>${defs ? `<defs>${defs}</defs>` : ''}${body}</svg>\n`;
}

/** Brand gradient definition (id given). Diagonal, top-left bright -> bottom-right blue. */
function gradientDef(id, x1 = 0, y1 = 0, x2 = 1, y2 = 1) {
  return `<linearGradient id="${id}" x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}">` +
    `<stop offset="0" stop-color="${PALETTE.bright}"/><stop offset="1" stop-color="${PALETTE.blue}"/></linearGradient>`;
}

function ensureDir(p) { fs.mkdirSync(path.dirname(p), { recursive: true }); }
function writeText(p, s) { ensureDir(p); fs.writeFileSync(p, s); }

/** Render an SVG string to a PNG at `width` px. opts.flatten = '#hex' removes alpha. */
async function render(svg, outPath, width, opts = {}) {
  ensureDir(outPath);
  const resvg = new Resvg(svg, { fitTo: { mode: 'width', value: width }, font: { loadSystemFonts: false } });
  let png = resvg.render().asPng();
  let img = sharp(png);
  if (opts.flatten) img = img.flatten({ background: opts.flatten }).removeAlpha();
  if (opts.webp) { await img.webp({ lossless: true }).toFile(outPath); return outPath; }
  await img.png({ compressionLevel: 9 }).toFile(outPath);
  return outPath;
}

function r(n, d = 3) { return +n.toFixed(d); }

module.exports = {
  ROOT, BRAND, PALETTE, MARK, mark, markCentered, font, text, measure, capHeight,
  wordmark, wordmarkWidth, waveform, wrap, svgDoc, gradientDef, render, writeText, ensureDir, r,
};
