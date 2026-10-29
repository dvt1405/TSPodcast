// Generates: docs/brand/concepts/*.svg (+ sheet PNG), brand/logo/*.svg, brand/logo/png/*.png
'use strict';
const path = require('path');
const L = require('./lib');
const { PALETTE: P, MARK } = L;

const LOGO = path.join(L.BRAND, 'logo');
const CONCEPTS = path.join(L.ROOT, 'docs', 'brand', 'concepts');

// --------------------------------------------------------------------- tiles
/** App-icon tile: gradient rounded square + white mark + coral dot. rounded=false -> full bleed. */
function tile(x, y, size, { rounded = true, fill = 0.6, gradId = 'tsg' } = {}) {
  const rx = rounded ? size * 0.225 : 0;
  return `<rect x="${x}" y="${y}" width="${size}" height="${size}" rx="${rx}" fill="url(#${gradId})"/>` +
    L.markCentered(x, y, size, fill, { line: P.white, dot: P.coral });
}

// ------------------------------------------------------------------ concepts
function conceptTile(inner) {
  return L.svgDoc(512, 512,
    `<rect width="512" height="512" rx="115" fill="url(#tsg)"/>` +
    `<g transform="translate(36 36) scale(4.4)">${inner}</g>`,
    { defs: L.gradientDef('tsg') });
}
const CONCEPT_SVGS = {
  // 1 — chosen: "Lên sóng"
  'concept-1-len-song.svg': conceptTile(
    `<path d="${MARK.t} ${MARK.s}" fill="none" stroke="#fff" stroke-width="${MARK.stroke}" stroke-linecap="round" stroke-linejoin="round"/>` +
    `<circle cx="${MARK.dot.cx}" cy="${MARK.dot.cy}" r="${MARK.dot.r}" fill="${P.coral}"/>`),
  // 2 — "Sóng treo": a T whose stem is an S-shaped wave (rejected: reads as the digit 5)
  'concept-2-song-treo.svg': conceptTile(
    `<path d="M19,25 H81 M50,25 A14.5,14.5 0 0 0 50,54 A16,16 0 0 1 50,86" fill="none" stroke="#fff" stroke-width="13" stroke-linecap="round"/>`),
  // 3 — "Bong bóng phát": speech bubble with a play triangle knocked out + two sound ticks
  'concept-3-bong-bong-phat.svg': conceptTile(
    `<path fill="#fff" fill-rule="evenodd" d="M50,16 A32,32 0 1 1 30.6,73.5 L17,82 L22.5,66 A32,32 0 0 1 50,16 Z M43,35 L43,61 L65,48 Z"/>` +
    `<path d="M86,36 A20,20 0 0 1 86,60" fill="none" stroke="${P.coral}" stroke-width="6" stroke-linecap="round"/>`),
};

// --------------------------------------------------------------------- logos
const capH = (size) => L.capHeight(size, 800);

/** Horizontal lockup. variant: color | dark | mono-black | mono-white */
function lockupH(variant) {
  const T = 160; // tile / mark height
  const pad = 40; // clear space (= 1/4 of the tile)
  const size = 112; // wordmark font size: cap height ≈ half the tile
  const gap = 44;
  const ww = L.wordmarkWidth(size);
  const W = Math.ceil(pad + T + gap + ww + pad);
  const H = pad * 2 + T;
  const cy = pad + T / 2;
  const base = cy + capH(size) / 2;
  const c = colorsFor(variant);
  let body = c.tile ? tile(pad, pad, T) : L.markCentered(pad, pad, T, 0.92, c.mark);
  body += L.wordmark(pad + T + gap, base, size, c.word);
  return L.svgDoc(W, H, body, { defs: L.gradientDef('tsg'), title: 'TSPodcast logo' });
}

/** Vertical lockup: tile above, wordmark centred below. */
function lockupV(variant) {
  const T = 220;
  const pad = 56;
  const size = 96;
  const ww = L.wordmarkWidth(size);
  const W = Math.ceil(Math.max(T, ww) + pad * 2);
  const gap = 44;
  const H = Math.ceil(pad + T + gap + capH(size) + pad + 6);
  const c = colorsFor(variant);
  let body = c.tile ? tile((W - T) / 2, pad, T) : L.markCentered((W - T) / 2, pad, T, 0.92, c.mark);
  body += L.wordmark(W / 2, pad + T + gap + capH(size), size, { ...c.word, anchor: 'middle' });
  return L.svgDoc(W, H, body, { defs: L.gradientDef('tsg'), title: 'TSPodcast logo' });
}

function colorsFor(variant) {
  switch (variant) {
    case 'color': return { tile: true, word: { ts: P.blue, rest: P.ink } };
    case 'dark': return { tile: true, word: { ts: '#8FB0FF', rest: P.white } };
    case 'mono-black': return { tile: false, mark: { line: P.ink, dot: P.ink }, word: { ts: P.ink, rest: P.ink } };
    case 'mono-white': return { tile: false, mark: { line: P.white, dot: P.white }, word: { ts: P.white, rest: P.white } };
    default: throw new Error(variant);
  }
}

function markSvg(colors, size = 512) {
  return L.svgDoc(size, size, L.markCentered(0, 0, size, 0.84, colors), { title: 'TSPodcast mark' });
}
function wordmarkSvg(word) {
  const size = 120;
  const W = Math.ceil(L.wordmarkWidth(size) + 48);
  const H = Math.ceil(capH(size) + 48 + 30); // room for descender of "p"? (none; keep breathing room)
  return L.svgDoc(W, H, L.wordmark(24, 24 + capH(size), size, word), { title: 'TSPodcast wordmark' });
}
function iconSvg(rounded) {
  return L.svgDoc(1024, 1024, tile(0, 0, 1024, { rounded, fill: 0.62 }), { defs: L.gradientDef('tsg'), title: 'TSPodcast icon' });
}

// on-background previews make the white / dark variants checkable as PNGs
function onBg(svg, bg) {
  const m = svg.match(/width="(\d+)" height="(\d+)"/);
  return svg.replace(/(<\/defs>|<\/title>)/, `$1<rect width="${m[1]}" height="${m[2]}" fill="${bg}"/>`);
}

async function main() {
  // concepts
  for (const [name, svg] of Object.entries(CONCEPT_SVGS)) L.writeText(path.join(CONCEPTS, name), svg);
  {
    // comparison sheet: each concept at 192 / 96 / 48 / 24 px, light + dark
    const names = Object.keys(CONCEPT_SVGS);
    const labels = ['1 · Lên sóng (chọn)', '2 · Sóng treo', '3 · Bong bóng phát'];
    const rowH = 250;
    let body = `<rect width="1100" height="${names.length * rowH + 30}" fill="#F4F6FB"/>`;
    names.forEach((n, i) => {
      const inner = CONCEPT_SVGS[n].replace(/^<svg[^>]*>|<\/svg>\s*$/g, '').replace(/<title>.*?<\/title>/, '').replace(/<defs>.*?<\/defs>/, '');
      const y = 30 + i * rowH;
      body += L.text(labels[i], { x: 30, y: y + 18, size: 22, weight: 700 });
      let x = 30;
      for (const s of [192, 96, 48, 24]) {
        body += `<g transform="translate(${x} ${y + 36}) scale(${s / 512})">${inner}</g>`;
        x += s + 30;
      }
      body += `<rect x="${x}" y="${y + 36}" width="${1100 - x - 30}" height="192" rx="16" fill="#0E0F14"/>`;
      body += `<g transform="translate(${x + 40} ${y + 84}) scale(${96 / 512})">${inner}</g>`;
      body += `<g transform="translate(${x + 170} ${y + 108}) scale(${48 / 512})">${inner}</g>`;
      body += `<g transform="translate(${x + 250} ${y + 120}) scale(${24 / 512})">${inner}</g>`;
    });
    const sheet = L.svgDoc(1100, names.length * rowH + 30, body, { defs: L.gradientDef('tsg') });
    await L.render(sheet, path.join(CONCEPTS, 'concepts-sheet.png'), 1100);
  }

  // master SVGs
  const files = {
    'tspodcast-icon.svg': iconSvg(true),
    'tspodcast-icon-square.svg': iconSvg(false),
    'tspodcast-mark.svg': markSvg({ line: P.blue, dot: P.coral }),
    'tspodcast-mark-mono-black.svg': markSvg({ line: P.ink, dot: P.ink }),
    'tspodcast-mark-mono-white.svg': markSvg({ line: P.white, dot: P.white }),
    'tspodcast-mark-white.svg': markSvg({ line: P.white, dot: P.coral }),
    'tspodcast-wordmark.svg': wordmarkSvg({ ts: P.blue, rest: P.ink }),
    'tspodcast-wordmark-white.svg': wordmarkSvg({ ts: P.white, rest: P.white }),
  };
  for (const v of ['color', 'dark', 'mono-black', 'mono-white']) {
    const suffix = v === 'color' ? '' : `-${v}`;
    files[`tspodcast-lockup-horizontal${suffix}.svg`] = lockupH(v);
    files[`tspodcast-lockup-vertical${suffix}.svg`] = lockupV(v);
  }
  for (const [n, s] of Object.entries(files)) L.writeText(path.join(LOGO, n), s);

  // PNG exports
  const png = path.join(LOGO, 'png');
  for (const s of [16, 32, 48, 64, 128, 256, 512, 1024]) await L.render(files['tspodcast-icon.svg'], path.join(png, `tspodcast-icon-${s}.png`), s);
  for (const s of [256, 512, 1024]) {
    await L.render(files['tspodcast-mark.svg'], path.join(png, `tspodcast-mark-${s}.png`), s);
    await L.render(files['tspodcast-mark-mono-black.svg'], path.join(png, `tspodcast-mark-mono-black-${s}.png`), s);
    await L.render(files['tspodcast-mark-mono-white.svg'], path.join(png, `tspodcast-mark-mono-white-${s}.png`), s);
  }
  for (const n of Object.keys(files).filter((k) => k.includes('lockup') || k.includes('wordmark'))) {
    const base = n.replace('.svg', '');
    const w = +files[n].match(/width="(\d+)"/)[1];
    await L.render(files[n], path.join(png, `${base}.png`), w);
    await L.render(files[n], path.join(png, `${base}@2x.png`), w * 2);
  }
  // preview board for review (all variants on their intended backgrounds)
  {
    const items = [
      ['tspodcast-lockup-horizontal.svg', '#FFFFFF'], ['tspodcast-lockup-horizontal-dark.svg', P.night],
      ['tspodcast-lockup-horizontal-mono-black.svg', '#FFFFFF'], ['tspodcast-lockup-horizontal-mono-white.svg', P.blue],
    ];
    let y = 0; let body = '';
    for (const [n, bg] of items) {
      const s = files[n]; const w = +s.match(/width="(\d+)"/)[1]; const h = +s.match(/height="(\d+)"/)[1];
      body += `<rect x="0" y="${y}" width="1000" height="${h}" fill="${bg}"/>` +
        `<g transform="translate(${(1000 - w) / 2} ${y})">${s.replace(/^<svg[^>]*>|<\/svg>\s*$/g, '').replace(/<title>.*?<\/title>/, '')}</g>`;
      y += h;
    }
    const vItems = [['tspodcast-lockup-vertical.svg', '#FFFFFF'], ['tspodcast-lockup-vertical-dark.svg', P.night]];
    let x = 0; let vh = 0;
    for (const [n, bg] of vItems) {
      const s = files[n]; const w = +s.match(/width="(\d+)"/)[1]; const h = +s.match(/height="(\d+)"/)[1];
      body += `<rect x="${x}" y="${y}" width="500" height="${h}" fill="${bg}"/>` +
        `<g transform="translate(${x + (500 - w) / 2} ${y})">${s.replace(/^<svg[^>]*>|<\/svg>\s*$/g, '').replace(/<title>.*?<\/title>/, '')}</g>`;
      x += 500; vh = Math.max(vh, h);
    }
    // ids collide between embedded docs; all use the same gradient so one def is enough
    const board = L.svgDoc(1000, y + vh, body.replace(/<defs>.*?<\/defs>/g, ''), { defs: L.gradientDef('tsg') });
    await L.render(board, path.join(LOGO, 'logo-overview.png'), 1000);
  }
  console.log('logo: done');
  return files;
}

module.exports = { tile, main };
if (require.main === module) main().catch((e) => { console.error(e); process.exit(1); });
