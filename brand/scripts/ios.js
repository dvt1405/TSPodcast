// Generates the iOS launch screen assets from the mark geometry (same look as the Android 12+
// splash: brand blue background, white T+S mark with the coral on-air dot).
//   iosApp/TSPodcast/Resources/Assets.xcassets/LaunchLogo.imageset        @1x/@2x/@3x PNG
//   iosApp/TSPodcast/Resources/Assets.xcassets/LaunchBackground.colorset  Xanh TS, Light and Dark
// Info.plist `UILaunchScreen` points at both by name (UIImageName / UIColorName).
'use strict';
const path = require('path');
const L = require('./lib');
const { PALETTE: P } = L;

const ASSETS = path.join(L.ROOT, 'iosApp', 'TSPodcast', 'Resources', 'Assets.xcassets');

// Logo canvas in points. iOS draws the launch image at its point size, centred. 120 pt keeps
// the mark close to the Android splash icon (140 of a 288 dp canvas shown at ~240 dp).
const LOGO_PT = 120;
const LOGO_FILL = 0.95;

function launchLogoSvg() {
  const body = L.markCentered(0, 0, LOGO_PT, LOGO_FILL, { line: P.white, dot: P.coral });
  return L.svgDoc(LOGO_PT, LOGO_PT, body, { title: 'TSPodcast launch logo' });
}

function srgb(hex) {
  const h = hex.replace('#', '').toUpperCase();
  return {
    'color-space': 'srgb',
    components: { alpha: '1.000', red: `0x${h.slice(0, 2)}`, green: `0x${h.slice(2, 4)}`, blue: `0x${h.slice(4, 6)}` },
  };
}

const json = (o) => `${JSON.stringify(o, null, 2)}\n`;

async function main() {
  const svg = launchLogoSvg();
  const set = path.join(ASSETS, 'LaunchLogo.imageset');
  const images = [];
  for (const scale of [1, 2, 3]) {
    const name = scale === 1 ? 'LaunchLogo.png' : `LaunchLogo@${scale}x.png`;
    await L.render(svg, path.join(set, name), LOGO_PT * scale);
    images.push({ filename: name, idiom: 'universal', scale: `${scale}x` });
  }
  L.writeText(path.join(set, 'Contents.json'), json({ images, info: { author: 'xcode', version: 1 } }));

  // Same blue in Dark Mode: the launch screen is a brand moment, like the Android splash.
  L.writeText(path.join(ASSETS, 'LaunchBackground.colorset', 'Contents.json'), json({
    colors: [
      { idiom: 'universal', color: srgb(P.blue) },
      { idiom: 'universal', appearances: [{ appearance: 'luminosity', value: 'dark' }], color: srgb(P.blue) },
    ],
    info: { author: 'xcode', version: 1 },
  }));
  console.log('ios: LaunchLogo.imageset, LaunchBackground.colorset');
}

module.exports = { main };
if (require.main === module) main().catch((e) => { console.error(e); process.exit(1); });
