// Regenerates every brand asset:  cd brand/scripts && npm install && npm run build
// Order matters only for fonts (needed by everything that sets text).
'use strict';
const { fetchFonts } = require('./fetch-fonts');

(async () => {
  await fetchFonts();
  await require('./logo').main();
  await require('./android').main();
  await require('./ios').main();
  await require('./store').main();
  const frame = require('./frame');
  frame.writeTemplates();
  await frame.examples();
  await require('./video').main();
  console.log('brand: all assets regenerated');
})().catch((e) => { console.error(e); process.exit(1); });
