// Title cards for the demo video: intro + outro, landscape 1920×1080 and portrait 1080×1920.
//   brand/video/tspodcast-{intro,outro}-{1920x1080,1080x1920}.png (+ src/*.svg)
'use strict';
const path = require('path');
const L = require('./lib');
const { tile } = require('./logo');
const { TAGLINE, DESCRIPTOR, nightGradient, glow } = require('./store');
const { PALETTE: P } = L;

const OUT = path.join(L.BRAND, 'video');
const defs = () => L.gradientDef('tsg') + nightGradient('bg') + glow('glow', P.bright, 0.5);

function base(W, H) {
  const m = Math.min(W, H);
  return `<rect width="${W}" height="${H}" fill="url(#bg)"/>` +
    `<circle cx="${W / 2}" cy="${H * 0.42}" r="${m * 0.75}" fill="url(#glow)"/>` +
    L.waveform(W * 0.06, H * (W > H ? 0.88 : 0.8), W * 0.88, m * 0.11, { n: W > H ? 44 : 26, opacity: 0.12, seed: 1.1 });
}

/** Centred stack: tile, wordmark, then text lines [{t, size, weight, fill, gap}] */
function stack(W, H, { T, word, lines, cy }) {
  const wordCap = L.capHeight(word);
  const linesH = lines.reduce((a, l) => a + l.gap + l.size * 0.72, 0);
  const total = T + word * 0.5 + wordCap + linesH;
  let y = cy - total / 2;
  let b = tile((W - T) / 2, y, T);
  y += T + word * 0.5 + wordCap;
  b += L.wordmark(W / 2, y, word, { ts: '#8FB0FF', rest: P.white, anchor: 'middle' });
  for (const l of lines) {
    y += l.gap + l.size * 0.72;
    b += L.text(l.t, { x: W / 2, y, size: l.size, weight: l.weight, fill: l.fill, anchor: 'middle', tracking: l.tracking || 0 });
  }
  return b;
}

function intro(W, H) {
  const land = W > H; const u = Math.min(W, H) / 1080;
  return L.svgDoc(W, H, base(W, H) + stack(W, H, {
    T: 220 * u, word: (land ? 120 : 112) * u, cy: H * (land ? 0.46 : 0.44),
    lines: [
      { t: TAGLINE, size: 56 * u, weight: 700, fill: P.white, gap: 64 * u, tracking: -6 },
      { t: DESCRIPTOR, size: 36 * u, weight: 500, fill: '#C9D6FF', gap: 46 * u },
    ],
  }), { defs: defs(), title: 'TSPodcast — intro' });
}

function outro(W, H) {
  const land = W > H; const u = Math.min(W, H) / 1080;
  let b = base(W, H) + stack(W, H, {
    T: 180 * u, word: 104 * u, cy: H * (land ? 0.42 : 0.42),
    lines: [
      { t: 'Tải miễn phí trên Google Play', size: 58 * u, weight: 800, fill: P.white, gap: 76 * u, tracking: -8 },
      { t: TAGLINE, size: 38 * u, weight: 500, fill: '#C9D6FF', gap: 46 * u },
    ],
  });
  // coral pill "call to action" under the copy
  const pw = 520 * u; const ph = 96 * u; const px = (W - pw) / 2; const py = H * (land ? 0.74 : 0.66);
  b += `<rect x="${px}" y="${py}" width="${pw}" height="${ph}" rx="${ph / 2}" fill="${P.coral}"/>`;
  b += L.text('Nghe ngay', { x: W / 2, y: py + ph / 2 + L.capHeight(40 * u, 800) / 2, size: 40 * u, weight: 800, fill: P.night, anchor: 'middle' }); // night on coral = 6.5:1
  return L.svgDoc(W, H, b.replace(/<g fill="#FFFFFF" opacity="0.12">[\s\S]*?<\/g>/, ''), { defs: defs(), title: 'TSPodcast — outro' });
}

async function main() {
  for (const [W, H] of [[1920, 1080], [1080, 1920]]) {
    for (const [name, fn] of [['intro', intro], ['outro', outro]]) {
      const svg = fn(W, H);
      L.writeText(path.join(OUT, 'src', `tspodcast-${name}-${W}x${H}.svg`), svg);
      await L.render(svg, path.join(OUT, `tspodcast-${name}-${W}x${H}.png`), W, { flatten: P.night });
    }
  }
  console.log('video: done');
}

module.exports = { main };
if (require.main === module) main().catch((e) => { console.error(e); process.exit(1); });
