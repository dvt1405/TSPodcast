#!/usr/bin/env node
// Store screenshot framer: wraps a raw device screenshot in brand artwork with a headline.
//
//   node frame.js --preset play  --headline "Nghe podcast Việt\nmỗi ngày" --sub "..." in.png out.png
//   node frame.js --preset ios69 --theme light --headline "..." in.png out.png
//   node frame.js --batch ../store/screenshots/examples.json      (see that file for the format)
//   node frame.js --templates                                     (re-writes brand/store/templates/*.svg)
//
// Presets:  play  = Google Play phone 1080×1920     ios69 = App Store 6.9" 1320×2868
// Themes:   brand (blue gradient, white text — default) | light (mist background, ink text)
// Text is converted to outlines with Be Vietnam Pro, so Vietnamese diacritics always render.
'use strict';
const fs = require('fs');
const path = require('path');
const sharp = require('sharp');
const L = require('./lib');
const { PALETTE: P } = L;

const PRESETS = {
  play: { W: 1080, H: 1920, label: 'Google Play phone' },
  ios69: { W: 1320, H: 2868, label: 'App Store iPhone 6.9"' },
};

/**
 * Layout shared by the PNG compositor and the editable SVG templates.
 * Everything is proportional to canvas width so both presets share one design.
 */
function layout(preset, shotW, shotH) {
  const { W, H } = PRESETS[preset];
  const u = W / 1080; // design unit
  const head = { x: 90 * u, top: 150 * u, size: 84 * u, lineH: 100 * u, maxW: W - 180 * u };
  const subSize = 40 * u;
  // device: fixed width, bottom bleeds off-canvas slightly so tall screens still feel large
  const devW = W * 0.78;
  const bezel = 18 * u;
  const screenW = devW - bezel * 2;
  const screenH = screenW * (shotH / shotW);
  const devH = screenH + bezel * 2;
  return { W, H, u, head, subSize, devW, devH, bezel, screenW, screenH, radius: 64 * u };
}

function themeColors(theme) {
  return theme === 'light'
    ? { bg: `<rect width="100%" height="100%" fill="${P.mist}"/>`, head: P.ink, sub: P.slate, bars: P.blue, barsOp: 0.08, device: P.ink }
    : { bg: '<rect width="100%" height="100%" fill="url(#bg)"/>', head: P.white, sub: '#C9D6FF', bars: P.white, barsOp: 0.1, device: '#0A0F24' };
}
const DEFS = `<linearGradient id="bg" x1="0" y1="0" x2="0.6" y2="1"><stop offset="0" stop-color="${P.bright}"/>` +
  `<stop offset="0.55" stop-color="${P.blue}"/><stop offset="1" stop-color="${P.night}"/></linearGradient>` +
  `<filter id="shadow" x="-20%" y="-10%" width="140%" height="130%"><feDropShadow dx="0" dy="24" stdDeviation="36" flood-color="#000" flood-opacity="0.35"/></filter>`;

/**
 * Builds the framed SVG. `href` is the screenshot (data URI or relative path).
 * outline=true -> text as paths (for rendering); false -> live <text> (for editable templates).
 */
function frameSvg({ preset, theme = 'brand', headline, sub = '', href, shotW, shotH, outline = true }) {
  const k = layout(preset, shotW, shotH);
  const c = themeColors(theme);
  const hOpts = { weight: 800, size: k.head.size, tracking: -14 };
  // "\n" in a headline forces a break (useful for natural Vietnamese phrasing); long lines still wrap
  const lines = headline.split('\n').flatMap((part) => L.wrap(part.trim(), k.head.maxW, hOpts));
  let b = c.bg;
  let y = k.head.top + k.head.size * 0.78;
  const textEl = (str, opts) => (outline
    ? L.text(str, opts)
    : `<text x="${L.r(opts.x)}" y="${L.r(opts.y)}" font-family="Be Vietnam Pro, sans-serif" font-weight="${opts.weight}" font-size="${L.r(opts.size)}" fill="${opts.fill}" letter-spacing="${L.r((opts.tracking || 0) * opts.size / 1000)}">${esc(str)}</text>`);
  // coral on-air dot as a bullet before the first line
  b += `<circle cx="${L.r(k.head.x + 14 * k.u)}" cy="${L.r(k.head.top - 46 * k.u)}" r="${L.r(14 * k.u)}" fill="${P.coral}"/>`;
  for (const line of lines) {
    b += textEl(line, { x: k.head.x, y, size: k.head.size, weight: 800, fill: c.head, tracking: -14 });
    y += k.head.lineH;
  }
  if (sub) {
    const subLines = L.wrap(sub, k.head.maxW, { weight: 500, size: k.subSize });
    y += 6 * k.u;
    for (const line of subLines) { b += textEl(line, { x: k.head.x, y, size: k.subSize, weight: 500, fill: c.sub }); y += k.subSize * 1.3; }
  }
  const devX = (k.W - k.devW) / 2;
  const devY = Math.max(y + 50 * k.u, k.H - k.devH * 0.93); // let the device bleed off the bottom when tall
  const sx = devX + k.bezel; const sy = devY + k.bezel; const sr = k.radius - k.bezel * 0.6;
  b += `<rect x="${L.r(devX)}" y="${L.r(devY)}" width="${L.r(k.devW)}" height="${L.r(k.devH)}" rx="${L.r(k.radius)}" fill="${c.device}" filter="url(#shadow)"/>`;
  b += `<clipPath id="screen"><rect x="${L.r(sx)}" y="${L.r(sy)}" width="${L.r(k.screenW)}" height="${L.r(k.screenH)}" rx="${L.r(sr)}"/></clipPath>`;
  b += `<image x="${L.r(sx)}" y="${L.r(sy)}" width="${L.r(k.screenW)}" height="${L.r(k.screenH)}" preserveAspectRatio="xMidYMid slice" clip-path="url(#screen)" href="${href}" xlink:href="${href}"/>`;
  return L.svgDoc(k.W, k.H, b, { defs: DEFS, title: `TSPodcast store screenshot — ${PRESETS[preset].label}` });
}

function esc(s) { return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }

async function frame({ preset = 'play', theme = 'brand', headline, sub, input, output }) {
  if (!PRESETS[preset]) throw new Error(`unknown preset ${preset} (play | ios69)`);
  if (!headline) throw new Error('--headline is required');
  const meta = await sharp(input).metadata();
  const buf = await sharp(input).png().toBuffer();
  const href = `data:image/png;base64,${buf.toString('base64')}`;
  const svg = frameSvg({ preset, theme, headline, sub, href, shotW: meta.width, shotH: meta.height });
  // App Store rejects screenshots with alpha -> always flatten
  await L.render(svg, output, PRESETS[preset].W, { flatten: theme === 'light' ? P.mist : P.night });
  return output;
}

async function batch(file) {
  const cfg = JSON.parse(fs.readFileSync(file, 'utf8'));
  const base = path.dirname(path.resolve(file));
  const outDir = path.resolve(base, cfg.outDir || 'out');
  const presets = cfg.presets || ['play', 'ios69'];
  for (const [i, s] of cfg.screens.entries()) {
    for (const preset of presets) {
      const name = `${String(i + 1).padStart(2, '0')}-${s.id || 'screen'}-${preset}.png`;
      await frame({ preset, theme: s.theme || cfg.theme, headline: s.headline, sub: s.sub, input: path.resolve(base, s.screenshot), output: path.join(outDir, name) });
      console.log('framed', name);
    }
  }
}

/** Editable templates: live <text> + a linked placeholder image, for Figma/Inkscape/browser. */
function writeTemplates() {
  const dir = path.join(L.BRAND, 'store', 'templates');
  const examples = [
    ['play', 'play-phone-1080x1920.svg'],
    ['ios69', 'appstore-6.9in-1320x2868.svg'],
  ];
  for (const [preset, name] of examples) {
    const svg = frameSvg({ preset, headline: 'Nghe podcast Việt\nmỗi ngày', sub: 'Chương trình thịnh hành, cập nhật liên tục', href: 'screenshot.png', shotW: 1080, shotH: 2340, outline: false });
    L.writeText(path.join(dir, name), svg);
  }
}

/** Neutral placeholder "screenshot" used by the examples until real captures are dropped in. */
async function placeholder(out, label) {
  const W = 1080; const H = 2340;
  let b = `<rect width="${W}" height="${H}" fill="#F7F8FC"/>`;
  b += `<rect y="0" width="${W}" height="110" fill="#E6EAF4"/>`;
  b += L.text(label, { x: 60, y: 260, size: 64, weight: 800, fill: P.ink });
  for (let i = 0; i < 6; i++) {
    const y = 360 + i * 300;
    b += `<rect x="60" y="${y}" width="240" height="240" rx="28" fill="#D9E2F7"/>`;
    b += `<rect x="340" y="${y + 40}" width="${560 - (i % 3) * 80}" height="40" rx="20" fill="#C9D3EA"/>`;
    b += `<rect x="340" y="${y + 110}" width="${420 - (i % 2) * 90}" height="32" rx="16" fill="#E1E6F2"/>`;
  }
  b += `<rect y="${H - 210}" width="${W}" height="210" fill="#FFFFFF"/>`;
  b += L.text('Ảnh minh hoạ — thay bằng ảnh chụp màn hình thật', { x: W / 2, y: H - 95, size: 34, weight: 500, fill: P.slate, anchor: 'middle' });
  await L.render(L.svgDoc(W, H, b), out, W);
}

async function examples() {
  const dir = path.join(L.BRAND, 'store', 'screenshots');
  const cfgPath = path.join(dir, 'examples.json');
  const cfg = JSON.parse(fs.readFileSync(cfgPath, 'utf8'));
  for (const s of cfg.screens) {
    const p = path.resolve(dir, s.screenshot);
    if (!fs.existsSync(p)) await placeholder(p, s.placeholderLabel || s.id);
  }
  await batch(cfgPath);
}

function parseArgs(argv) {
  const o = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a.startsWith('--')) {
      const key = a.slice(2);
      if (['templates', 'examples'].includes(key)) o[key] = true; else o[key] = argv[++i];
    } else o._.push(a);
  }
  return o;
}

async function main() {
  const a = parseArgs(process.argv.slice(2));
  if (a.templates) return writeTemplates();
  if (a.examples) { writeTemplates(); return examples(); }
  if (a.batch) return batch(a.batch);
  if (a._.length !== 2) {
    console.error('usage: node frame.js [--preset play|ios69] [--theme brand|light] --headline "..." [--sub "..."] in.png out.png\n' +
      '       node frame.js --batch config.json | --templates | --examples');
    process.exit(2);
  }
  await frame({ preset: a.preset, theme: a.theme, headline: a.headline, sub: a.sub, input: a._[0], output: a._[1] });
  console.log('wrote', a._[1]);
}

module.exports = { frame, frameSvg, batch, writeTemplates, examples, PRESETS };
if (require.main === module) main().catch((e) => { console.error(e.message || e); process.exit(1); });
