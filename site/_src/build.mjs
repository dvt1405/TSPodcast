// Generates the static HTML pages of the TSPodcast product site (Firebase Hosting site podcast-25c93).
//
//   node site/_src/build.mjs
//
// No dependencies. The generated .html files are committed, so deploying needs no build step:
// this script only exists so header, footer, CSS (inlined for speed), SEO tags and JSON-LD stay
// identical on every page. Edit the sources in site/_src/ and re-run it; never hand-edit the output.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const SRC = dirname(fileURLToPath(import.meta.url));
const OUT = join(SRC, '..');
const REPO = join(OUT, '..');

const ORIGIN = 'https://podcast-25c93.web.app';
const PLAY = 'https://play.google.com/store/apps/details?id=tss.t.podcast';
const WEB = 'https://tspodcast.vercel.app';
const EMAIL = 'admin@tuandv.me';
const UPDATED = '2026-11-01';
const DEFAULT_UTM = 'utm_source=website&utm_medium=landing';

// Links work without JavaScript and already carry the default campaign values.
const playHref = `${PLAY}&referrer=${encodeURIComponent(DEFAULT_UTM)}`;
const webHref = (path = '/') => `${WEB}${path}?${DEFAULT_UTM}`;

// Copies utm_* and gclid from the page URL into every a[data-cta]: Google Play gets them as the
// install `referrer`, the web app as normal query parameters. Keep it < 1 KB. If you change it,
// update the sha256 in the Content-Security-Policy of firebase.json (this script checks).
const CAMPAIGN_JS = `(function(){try{var q=new URLSearchParams(location.search),p=new URLSearchParams();["utm_source","utm_medium","utm_campaign","utm_content","utm_term","gclid"].forEach(function(n){var v=q.get(n);v&&p.set(n,v)});p.has("utm_source")||p.set("utm_source","website");p.has("utm_medium")||p.set("utm_medium","landing");var r=p.toString();document.querySelectorAll("a[data-cta]").forEach(function(a){var u=new URL(a.href);if(u.hostname=="play.google.com")u.searchParams.set("referrer",r);else if(u.hostname=="tspodcast.vercel.app")u.search=r;else return;a.href=u.href})}catch(e){}})();`;
// Scroll reveal. Every [data-reveal] gets `--d` (stagger index among its siblings) and is revealed
// by an IntersectionObserver; no layout is read synchronously. html.js is set last, so without
// JavaScript (or if this script fails) nothing is ever hidden. The attribute is removed after the
// reveal so later hover transitions on cards are not delayed by the stagger.
const REVEAL_JS = `(function(){var l=document.querySelectorAll("[data-reveal]");if(!l.length||!("IntersectionObserver"in window))return;var o=new IntersectionObserver(function(t){t.forEach(function(x){if(!x.isIntersecting)return;var n=x.target;o.unobserve(n);n.classList.add("in");setTimeout(function(){n.removeAttribute("data-reveal")},1200)})},{rootMargin:"0px 0px -8% 0px"});[].forEach.call(l,function(n){n.style.setProperty("--d",[].indexOf.call(n.parentNode.children,n)%6);o.observe(n)});document.documentElement.classList.add("js")})();`;
const sha = (s) => `sha256-${createHash('sha256').update(s).digest('base64')}`;
const SCRIPT_HASHES = [sha(CAMPAIGN_JS), sha(REVEAL_JS)];

const css = readFileSync(join(SRC, 'site.css'), 'utf8')
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/\s+/g, ' ')
  .replace(/\s*([{};:,>])\s*/g, '$1')
  .replace(/;}/g, '}')
  .trim();

const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;');
const strip = (html) => html.replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim();
const ld = (obj) => `<script type="application/ld+json">${JSON.stringify(obj).replace(/</g, '\\u003c')}</script>`;

// ---------------------------------------------------------------- icons
const I = {
  mic: '<rect x="9" y="2" width="6" height="12" rx="3"/><path d="M5 10v1a7 7 0 0 0 14 0v-1M12 18v4M8 22h8"/>',
  radio: '<rect x="3" y="8" width="18" height="13" rx="2"/><path d="M7 8l10-5"/><circle cx="8" cy="14.5" r="2.5"/><path d="M14 13h4M14 16h4"/>',
  music: '<path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/>',
  timer: '<circle cx="12" cy="13" r="8"/><path d="M12 9v4l2.5 2.5M9 2h6"/>',
  phone: '<rect x="6" y="2" width="12" height="20" rx="2.5"/><path d="M10 11v4l3.5-2z"/><path d="M10 18.5h4"/>',
  shield: '<path d="M12 2l8 3v6c0 5-3.5 9.5-8 11-4.5-1.5-8-6-8-11V5z"/><path d="M8.5 12l2.5 2.5 4.5-5"/>',
  trend: '<path d="M3 17l6-6 4 4 8-8"/><path d="M14 7h7v7"/>',
  waves: '<circle cx="12" cy="12" r="2"/><path d="M16.2 7.8a6 6 0 0 1 0 8.4M7.8 16.2a6 6 0 0 1 0-8.4M19 5a10 10 0 0 1 0 14M5 19A10 10 0 0 1 5 5"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="M20 20l-4-4"/>',
  headphones: '<path d="M3 14v-2a9 9 0 0 1 18 0v2"/><path d="M21 15a2 2 0 0 1-2 2h-1v-6h1a2 2 0 0 1 2 2zM3 15a2 2 0 0 0 2 2h1v-6H5a2 2 0 0 0-2 2z"/>',
};
const icon = (name) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${I[name]}</svg>`;
const playIcon = '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M6 3.5v17a1 1 0 0 0 1.5.86l14-8.5a1 1 0 0 0 0-1.72l-14-8.5A1 1 0 0 0 6 3.5z"/></svg>';

// ---------------------------------------------------------------- content
const FEATURES = {
  // Four short items per card so every card in a row reads the same way.
  podcast: { icon: 'mic', title: 'Podcast Việt và thế giới', items: [
    'Podcast thịnh hành, ưu tiên chương trình tiếng Việt',
    'Hơn 100 chủ đề cho mục "Có thể bạn yêu thích"',
    'Tìm theo tên chương trình hoặc từ khoá',
    'Podcast live: nghe tập đang phát trực tiếp',
  ] },
  radio: { icon: 'radio', title: 'Radio online', items: [
    'Kênh phát thanh trực tiếp ngay trên điện thoại',
    'Có các kênh của VOV và VOH',
    'Chạm vào kênh là phát',
    'Tắt màn hình vẫn nghe tiếp',
  ] },
  music: { icon: 'music', title: 'Kho nhạc mở', items: [
    'Nhạc do nghệ sĩ tự phát hành',
    'Feed chuẩn Podcasting 2.0 từ Podcast Index',
    'Bộ lọc "Nhạc" ngay trong tìm kiếm',
    'Phát nền và điều khiển như với podcast',
  ] },
  player: { icon: 'timer', title: 'Nghe theo cách của bạn', items: [
    'Tốc độ 0,5× đến 3,0×, nhớ riêng từng chương trình',
    'Hẹn giờ tắt theo số phút hoặc khi hết tập',
    'Lùi 15 giây, tới 30 giây',
    'Mở lại là nghe tiếp từ chỗ đã dừng',
  ] },
  background: { icon: 'phone', title: 'Nghe nền, không gián đoạn', items: [
    'Tắt màn hình hay mở app khác vẫn nghe tiếp',
    'Điều khiển ở màn hình khoá và tai nghe Bluetooth',
    'Trình phát thu nhỏ khi bạn duyệt app',
    'Bấm tim để gom tập yêu thích về một chỗ',
  ] },
  safety: { icon: 'shield', title: 'Nội dung an toàn hơn', items: [
    'Ẩn nội dung dành cho người lớn',
    'Ẩn chương trình bạn không muốn thấy',
    'Báo cáo nội dung ngay trong app',
    'Danh sách chặn chương trình rác, cờ bạc',
  ] },
};

const SHOTS = [
  ['01', 'Podcast Việt và thế giới mỗi ngày: màn hình Khám phá với chương trình thịnh hành'],
  ['02', 'Radio online, chạm là nghe: danh sách kênh phát thanh trực tiếp'],
  ['03', 'Tắt màn hình vẫn nghe tiếp: trình phát đang phát một tập truyện cổ tích'],
  ['04', 'Tìm đúng chủ đề bạn quan tâm: kết quả tìm kiếm cho từ khoá kinh doanh'],
  ['05', 'Kho nhạc mở: các feed nhạc do nghệ sĩ phát hành và mục Live Podcasts'],
  ['06', 'Gom tập yêu thích về một chỗ: màn hình Yêu thích'],
];

const FAQ = [
  ['TSPodcast có mất phí không?',
    `Không. TSPodcast miễn phí: bạn tải app trên Google Play và dùng mọi tính năng mà không phải trả tiền. Bản web tại <a data-cta="faq-web" href="${esc(webHref('/'))}">tspodcast.vercel.app</a> cũng miễn phí.`],
  ['Tôi có cần tạo tài khoản không?',
    'Không cần. App không có đăng ký hay đăng nhập. Danh sách yêu thích, lịch sử nghe, vị trí đang nghe và các tuỳ chọn của bạn được lưu ngay trên điện thoại.'],
  ['Podcast, radio và nhạc trong app lấy từ đâu?',
    'Danh mục podcast và nhạc lấy từ Podcast Index, một thư mục podcast mở. Âm thanh được phát trực tiếp từ máy chủ của nhà sản xuất chương trình, còn radio phát từ máy chủ của các đài. Nội dung thuộc về người tạo ra nó. TSPodcast là ứng dụng độc lập, không liên kết với các đài phát thanh hay nhà sản xuất podcast.'],
  ['TSPodcast dùng được trên thiết bị nào?',
    `Hiện ứng dụng có trên Android qua Google Play. Trên máy tính hoặc thiết bị khác, bạn nghe được trên web tại <a data-cta="faq-web-devices" href="${esc(webHref('/'))}">tspodcast.vercel.app</a>.`],
  ['Tắt màn hình có nghe tiếp được không?',
    'Có. App phát nền khi bạn tắt màn hình hoặc mở app khác. Bạn điều khiển được từ thanh thông báo, màn hình khoá và tai nghe Bluetooth, và có thể hẹn giờ tắt theo số phút hoặc khi hết tập.'],
  ['Tôi gặp nội dung không phù hợp thì làm sao?',
    `Bạn có thể báo cáo nội dung hoặc ẩn chương trình ngay trong app, và bật tuỳ chọn ẩn nội dung dành cho người lớn trong Cài đặt. Yêu cầu gỡ nội dung xin gửi tới <a href="mailto:${EMAIL}">${EMAIL}</a>.`],
];

// Landing pages. Variants for campaigns live under /l/ (noindex, canonical = home).
// To add one: copy an entry, change path/copy/hero, run the script, add nothing to sitemap.xml.
const LANDINGS = [
  {
    file: 'index.html', path: '/', index: true,
    title: 'TSPodcast: Nghe podcast tiếng Việt, radio online miễn phí',
    description: 'Ứng dụng Android miễn phí để nghe podcast tiếng Việt và quốc tế, radio online và kho nhạc mở. Không cần tài khoản. Thế giới trong tai bạn.',
    h1: 'Nghe podcast tiếng Việt, radio online và nhạc miễn phí',
    lead: 'TSPodcast gom podcast Việt và thế giới, kênh phát thanh trực tiếp và kho nhạc mở vào một ứng dụng. Mở ra là nghe, không cần tài khoản.',
    hero: ['01', 'Màn hình Khám phá của TSPodcast với mục Podcasts thịnh hành và Kho nhạc'],
    webPath: '/', webLabel: 'Nghe ngay trên web',
    features: ['podcast', 'radio', 'music', 'player', 'background', 'safety'],
  },
  {
    file: 'l/radio.html', path: '/l/radio', index: false,
    title: 'Nghe radio online miễn phí trên điện thoại | TSPodcast',
    description: 'Nghe kênh phát thanh trực tiếp, podcast tiếng Việt và nhạc trong một ứng dụng Android miễn phí. Chạm là phát, không cần tài khoản.',
    h1: 'Nghe radio online miễn phí, chạm là phát',
    lead: 'Kênh phát thanh trực tiếp ngay trên điện thoại, cùng podcast tiếng Việt và kho nhạc mở. Tắt màn hình vẫn nghe, không cần tài khoản.',
    hero: ['02', 'Màn hình Radio của TSPodcast với danh sách kênh phát thanh trực tiếp'],
    webPath: '/radio', webLabel: 'Nghe radio trên web',
    features: ['radio', 'background', 'player', 'podcast', 'music', 'safety'],
  },
  {
    file: 'l/podcast.html', path: '/l/podcast', index: false,
    title: 'Nghe podcast tiếng Việt miễn phí mỗi ngày | TSPodcast',
    description: 'Podcast tiếng Việt và quốc tế thịnh hành, gợi ý theo hơn 100 chủ đề bạn chọn, nghe nền khi tắt màn hình. Ứng dụng Android miễn phí.',
    h1: 'Nghe podcast tiếng Việt mỗi ngày, miễn phí',
    lead: 'Chương trình thịnh hành, gợi ý theo hơn 100 chủ đề bạn chọn, tốc độ phát và hẹn giờ tắt tuỳ ý. Không cần tài khoản.',
    hero: ['04', 'Màn hình Tìm kiếm của TSPodcast với kết quả cho từ khoá kinh doanh'],
    webPath: '/trending', webLabel: 'Nghe podcast trên web',
    features: ['podcast', 'player', 'background', 'music', 'radio', 'safety'],
  },
];

// Legal pages: [vi, en] pairs that point to each other with hreflang.
const DOCS = [
  {
    key: 'privacy',
    vi: { file: 'privacy.html', path: '/privacy', title: 'Chính sách quyền riêng tư | TSPodcast', crumb: 'Chính sách quyền riêng tư',
      description: 'Dữ liệu TSPodcast xử lý, lý do, bên nhận, thời gian lưu trữ và quyền của bạn theo Luật Bảo vệ dữ liệu cá nhân. Không cần tài khoản.' },
    en: { file: 'en/privacy.html', path: '/en/privacy', title: 'Privacy Policy | TSPodcast', crumb: 'Privacy Policy',
      description: 'What data TSPodcast processes, why, who receives it, how long it is kept and your rights under Vietnam\'s Personal Data Protection Law.' },
  },
  {
    key: 'terms',
    vi: { file: 'terms.html', path: '/terms', title: 'Điều khoản sử dụng | TSPodcast', crumb: 'Điều khoản sử dụng',
      description: 'Điều khoản sử dụng ứng dụng TSPodcast miễn phí: nội dung của bên thứ ba, báo cáo và gỡ nội dung, giới hạn trách nhiệm, luật áp dụng.' },
    en: { file: 'en/terms.html', path: '/en/terms', title: 'Terms of Service | TSPodcast', crumb: 'Terms of Service',
      description: 'Terms of Service for the free TSPodcast app: third-party content, reports and takedown requests, limitation of liability, governing law.' },
  },
  {
    key: 'delete-data',
    vi: { file: 'delete-data.html', path: '/delete-data', title: 'Xoá dữ liệu | TSPodcast', crumb: 'Xoá dữ liệu',
      description: 'Cách xoá dữ liệu TSPodcast: xoá dữ liệu ứng dụng, tắt thống kê trong Cài đặt và gửi email yêu cầu xoá dữ liệu thống kê, báo lỗi.' },
    en: { file: 'en/delete-data.html', path: '/en/delete-data', title: 'Delete your data | TSPodcast', crumb: 'Delete your data',
      description: 'How to delete TSPodcast data: clear app data, turn off analytics in Settings and email us to delete analytics and crash data.' },
  },
];

// ---------------------------------------------------------------- layout
const T = {
  vi: { skip: 'Bỏ qua, tới nội dung chính', home: 'TSPodcast, trang chủ', nav: 'Điều hướng chính', features: 'Tính năng',
    shots: 'Hình ảnh', faq: 'Hỏi đáp', getApp: 'Tải app', crumbs: 'Đường dẫn', homeCrumb: 'Trang chủ', toc: 'Mục lục' },
  en: { skip: 'Skip to main content', home: 'TSPodcast home', nav: 'Main navigation', features: 'Features',
    shots: 'Screenshots', faq: 'FAQ', getApp: 'Get the app', crumbs: 'Breadcrumb', homeCrumb: 'Home', toc: 'Contents' },
};

function head(p) {
  const url = ORIGIN + p.path;
  const canonical = ORIGIN + (p.canonical ?? p.path);
  const ogLocale = p.lang === 'en' ? 'en_US' : 'vi_VN';
  const ogAlt = p.lang === 'en' ? 'vi_VN' : 'en_US';
  if (p.title.length > 60) throw new Error(`title > 60 chars (${p.title.length}): ${p.title}`);
  if (p.description.length > 155) throw new Error(`description > 155 chars (${p.description.length}): ${p.path}`);
  const alts = (p.alternates || []).map(([hl, path]) => `<link rel="alternate" hreflang="${hl}" href="${ORIGIN}${path}">`).join('\n');
  return `<!doctype html>
<html lang="${p.lang}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(p.title)}</title>
<meta name="description" content="${esc(p.description)}">
${p.noindex ? '<meta name="robots" content="noindex, follow">\n' : ''}<link rel="canonical" href="${canonical}">
${alts ? alts + '\n' : ''}<meta name="theme-color" content="#0B1A4A">
<link rel="icon" href="/favicon.ico" sizes="any">
<link rel="icon" href="/favicon.svg" type="image/svg+xml">
<link rel="apple-touch-icon" href="/apple-touch-icon.png">
<link rel="manifest" href="/site.webmanifest">
<meta property="og:type" content="website">
<meta property="og:site_name" content="TSPodcast">
<meta property="og:locale" content="${ogLocale}">
<meta property="og:locale:alternate" content="${ogAlt}">
<meta property="og:title" content="${esc(p.ogTitle || p.title)}">
<meta property="og:description" content="${esc(p.description)}">
<meta property="og:url" content="${p.noindex ? canonical : url}">
<meta property="og:image" content="${ORIGIN}/assets/img/og-image.png">
<meta property="og:image:type" content="image/png">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta property="og:image:alt" content="Logo TSPodcast và dòng chữ Thế giới trong tai bạn">
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:title" content="${esc(p.ogTitle || p.title)}">
<meta name="twitter:description" content="${esc(p.description)}">
<meta name="twitter:image" content="${ORIGIN}/assets/img/og-image.png">
<meta name="twitter:image:alt" content="Logo TSPodcast và dòng chữ Thế giới trong tai bạn">
<link rel="preload" href="/assets/fonts/be-vietnam-pro-800.woff2" as="font" type="font/woff2" crossorigin>
<link rel="preload" href="/assets/fonts/be-vietnam-pro-400.woff2" as="font" type="font/woff2" crossorigin>
${p.preloadImage ? `<link rel="preload" href="${p.preloadImage}" as="image" type="image/webp" fetchpriority="high" media="(min-width: 900px)">\n` : ''}<!-- Campaign / analytics tags: intentionally empty. A future Google tag + consent mode snippet goes here.
     Adding a script requires updating the Content-Security-Policy in firebase.json and the privacy policy. -->
<style>${css}</style>
${(p.jsonld || []).map(ld).join('\n')}
</head>`;
}

function header(lang, landing) {
  const t = T[lang];
  const base = landing ? '' : '/';
  return `<a class="skip" href="#main">${t.skip}</a>
<header class="site-header">
  <div class="wrap">
    <a class="brand" href="/" aria-label="${t.home}"><img src="/assets/img/tspodcast-lockup-horizontal-dark.svg" alt="TSPodcast" width="165" height="44"></a>
    <nav class="site-nav" aria-label="${t.nav}">
      <a class="nav-link" href="${base}#tinh-nang">${t.features}</a>
      <a class="nav-link" href="${base}#hinh-anh">${t.shots}</a>
      <a class="nav-link" href="${base}#hoi-dap">${t.faq}</a>
      <a class="nav-cta" id="cta-header-play" data-cta="header-play" href="${esc(playHref)}">${t.getApp}</a>
    </nav>
  </div>
</header>`;
}

function footer(lang) {
  const en = lang === 'en';
  return `<footer class="site-footer"${en ? ' lang="vi"' : ''}>
  <div class="wrap">
    <div class="cols">
      <div class="brand-col">
        <a class="logo" href="/" aria-label="TSPodcast, trang chủ"><img src="/assets/img/tspodcast-lockup-horizontal-dark.svg" alt="TSPodcast" width="150" height="40" loading="lazy"></a>
        <p>Thế giới trong tai bạn. Nghe podcast, radio online và nhạc miễn phí.</p>
      </div>
      <div>
        <h2>Nghe và tải</h2>
        <ul>
          <li><a id="cta-footer-web" data-cta="footer-web" href="${esc(webHref('/'))}">Nghe trên web</a></li>
          <li><a id="cta-footer-play" data-cta="footer-play" href="${esc(playHref)}">Tải trên Google Play</a></li>
          <li><a href="mailto:${EMAIL}">${EMAIL}</a></li>
        </ul>
      </div>
      <div>
        <h2>Pháp lý</h2>
        <ul>
          <li><a href="/privacy">Chính sách quyền riêng tư</a></li>
          <li><a href="/terms">Điều khoản sử dụng</a></li>
          <li><a href="/delete-data">Xoá dữ liệu</a></li>
          <li><a href="/en/privacy" lang="en" hreflang="en">Privacy Policy (English)</a></li>
          <li><a href="/en/terms" lang="en" hreflang="en">Terms of Service (English)</a></li>
        </ul>
      </div>
    </div>
    <div class="fine">
      <p>Ứng dụng độc lập: TSPodcast không liên kết với các đài phát thanh hay nhà sản xuất podcast. Nội dung podcast và âm nhạc thuộc về người tạo ra nó; dữ liệu thư mục do Podcast Index cung cấp.</p>
      <p>Google Play là nhãn hiệu của Google LLC.</p>
      <p>© 2026 TSPodcast</p>
    </div>
  </div>
</footer>`;
}

// ---------------------------------------------------------------- landing
function featureCard(key) {
  const f = FEATURES[key];
  return `<li class="card lift" data-reveal><div class="icon">${icon(f.icon)}</div><h3>${f.title}</h3><ul class="ticks">${f.items.map((i) => `<li>${i}</li>`).join('')}</ul></li>`;
}

const ORG = { '@type': 'Organization', '@id': `${ORIGIN}/#organization`, name: 'TSPodcast', url: `${ORIGIN}/`,
  logo: `${ORIGIN}/icon-512.png`, email: EMAIL, sameAs: [PLAY] };

function landingJsonLd(v) {
  return [{
    '@context': 'https://schema.org',
    '@graph': [
      ORG,
      { '@type': 'WebSite', '@id': `${ORIGIN}/#website`, url: `${ORIGIN}/`, name: 'TSPodcast', inLanguage: 'vi',
        publisher: { '@id': `${ORIGIN}/#organization` } },
      { '@type': 'SoftwareApplication', '@id': `${ORIGIN}/#app`, name: 'TSPodcast', url: `${ORIGIN}/`,
        description: v.description, operatingSystem: 'Android', applicationCategory: 'MultimediaApplication',
        inLanguage: 'vi', isAccessibleForFree: true, downloadUrl: PLAY, installUrl: PLAY,
        image: `${ORIGIN}/assets/img/og-image.png`,
        screenshot: SHOTS.map(([n]) => `${ORIGIN}/assets/screens/${n}.webp`),
        featureList: Object.values(FEATURES).map((f) => f.title),
        publisher: { '@id': `${ORIGIN}/#organization` },
        offers: { '@type': 'Offer', price: '0', priceCurrency: 'VND' } },
      { '@type': 'FAQPage', '@id': `${ORIGIN}/#faq`, inLanguage: 'vi',
        mainEntity: FAQ.map(([q, a]) => ({ '@type': 'Question', name: q, acceptedAnswer: { '@type': 'Answer', text: strip(a) } })) },
    ],
  }];
}

function landing(v) {
  const [heroN, heroAlt] = v.hero;
  const p = {
    lang: 'vi', path: v.path, canonical: v.index ? v.path : '/', noindex: !v.index,
    title: v.title, description: v.description, ogTitle: v.index ? 'TSPodcast: Thế giới trong tai bạn' : v.title,
    alternates: v.index ? [['vi', '/'], ['x-default', '/']] : [],
    preloadImage: `/assets/screens/${heroN}.webp`,
    jsonld: v.index ? landingJsonLd(v) : [],
  };
  const quick = [
    ['/trending', 'trend', 'Thịnh hành', 'Podcast đang được nghe nhiều', 'web-trending'],
    ['/radio', 'waves', 'Radio', 'Kênh phát thanh trực tiếp', 'web-radio'],
    ['/search', 'search', 'Tìm kiếm', 'Theo tên chương trình, từ khoá', 'web-search'],
  ];
  return `${head(p)}
<body>
${header('vi', true)}
<main id="main">
  <section class="hero" aria-labelledby="hero-title">
    <div class="wrap">
      <div class="hero-copy">
        <p class="hero-eyebrow"><span class="on-air" aria-hidden="true"></span>Thế giới trong tai bạn</p>
        <h1 id="hero-title">${v.h1}</h1>
        <p class="lead">${v.lead}</p>
        <div class="btn-row">
          <a class="btn btn-coral" id="cta-hero-play" data-cta="hero-play" href="${esc(playHref)}">${playIcon}Tải trên Google Play</a>
          <a class="btn btn-ghost" id="cta-hero-web" data-cta="hero-web" href="${esc(webHref(v.webPath))}">${icon('headphones')}${v.webLabel}</a>
        </div>
        <p class="note">Miễn phí, dành cho Android. Trên máy tính và thiết bị khác, bạn nghe ngay trên trình duyệt.</p>
      </div>
      <div class="hero-art">
        <img src="/assets/screens/${heroN}.webp" width="540" height="960" fetchpriority="high" alt="${esc(heroAlt)}">
        <div class="now-playing" aria-hidden="true"><span class="eq"><i></i><i></i><i></i><i></i></span>Podcast · Radio · Nhạc</div>
      </div>
    </div>
  </section>

  <section class="section" id="tinh-nang" aria-labelledby="features-title">
    <div class="wrap">
      <div class="section-head" data-reveal>
        <p class="kicker">Tính năng</p>
        <h2 id="features-title">Podcast, radio và nhạc gọn trong một app</h2>
        <p>Một trình phát quen thuộc cho mọi thứ bạn nghe. Giao diện tiếng Việt, chạm là nghe.</p>
      </div>
      <ul class="features">
        ${v.features.map(featureCard).join('\n        ')}
      </ul>
    </div>
  </section>

  <section class="section section-alt" id="hinh-anh" aria-labelledby="shots-title">
    <div class="wrap">
      <div class="section-head" data-reveal>
        <p class="kicker">Hình ảnh</p>
        <h2 id="shots-title">Xem TSPodcast trên điện thoại</h2>
      </div>
      <ul class="shots" data-reveal>
        ${SHOTS.map(([n, alt]) => `<li><img src="/assets/screens/${n}.webp" srcset="/assets/screens/${n}-300.webp 300w, /assets/screens/${n}.webp 540w" sizes="(min-width: 1024px) 180px, 196px" width="540" height="960" loading="lazy" decoding="async" alt="${esc(alt)}"></li>`).join('\n        ')}
      </ul>
    </div>
  </section>

  <section class="section" id="mien-phi" aria-labelledby="free-title">
    <div class="wrap free">
      <div class="free-panel" data-reveal>
        <h2 id="free-title">Miễn phí, không cần tài khoản</h2>
        <p>Tải về, mở app và nghe. Bạn không phải đăng ký, không phải nhập email hay số điện thoại.</p>
        <div class="btn-row">
          <a class="btn btn-coral" id="cta-free-play" data-cta="free-play" href="${esc(playHref)}">${playIcon}Tải trên Google Play</a>
        </div>
      </div>
      <ul class="free-list card" data-reveal>
        <li><span><strong>Miễn phí.</strong> Tải và dùng mọi tính năng mà không phải trả tiền.</span></li>
        <li><span><strong>Không cần đăng ký.</strong> Danh sách yêu thích, lịch sử và vị trí đang nghe được lưu ngay trên máy của bạn.</span></li>
        <li><span><strong>Bạn quyết định.</strong> Chọn có chia sẻ thống kê sử dụng và báo lỗi hay không ngay lần mở app đầu tiên, đổi lại bất cứ lúc nào trong Cài đặt.</span></li>
        <li><span><strong>Tiếng Việt.</strong> Giao diện tiếng Việt, ưu tiên chương trình tiếng Việt.</span></li>
      </ul>
    </div>
  </section>

  <section class="section section-alt" id="nghe-tren-web" aria-labelledby="web-title">
    <div class="wrap">
      <div class="section-head" data-reveal>
        <p class="kicker">Nghe trên web</p>
        <h2 id="web-title">Chưa cài app? Nghe ngay trên trình duyệt</h2>
        <p>Mở TSPodcast trên web từ máy tính hay điện thoại, không cần cài đặt.</p>
      </div>
      <ul class="quick">
        ${quick.map(([path, ic, label, sub, cta]) => `<li data-reveal><a class="card lift" id="cta-${cta}" data-cta="${cta}" href="${esc(webHref(path))}"><span class="icon">${icon(ic)}</span><span class="label"><strong>${label}</strong><span class="sub">${sub}</span></span><span class="arrow" aria-hidden="true">→</span></a></li>`).join('\n        ')}
      </ul>
    </div>
  </section>

  <section class="section" id="hoi-dap" aria-labelledby="faq-title">
    <div class="wrap faq-layout">
      <div class="section-head" data-reveal>
        <p class="kicker">Hỏi đáp</p>
        <h2 id="faq-title">Câu hỏi thường gặp</h2>
        <p>Chưa thấy câu trả lời? Gửi email tới <a href="mailto:${EMAIL}">${EMAIL}</a>.</p>
      </div>
      <div class="faq">
        ${FAQ.map(([q, a]) => `<details class="card" data-reveal><summary>${q}</summary><p>${a}</p></details>`).join('\n        ')}
      </div>
    </div>
  </section>
</main>
${footer('vi')}
<script>${CAMPAIGN_JS}</script>
<script>${REVEAL_JS}</script>
</body>
</html>
`;
}

// ---------------------------------------------------------------- legal pages
function doc(d, lang) {
  const page = d[lang];
  const other = d[lang === 'vi' ? 'en' : 'vi'];
  const t = T[lang];
  const content = readFileSync(join(SRC, 'content', `${d.key}.${lang}.html`), 'utf8').trim();
  const homeUrl = `${ORIGIN}/`;
  const p = {
    lang, path: page.path, title: page.title, description: page.description,
    alternates: [[lang, page.path], [lang === 'vi' ? 'en' : 'vi', other.path], ['x-default', d.vi.path]],
    jsonld: [{
      '@context': 'https://schema.org', '@type': 'BreadcrumbList',
      itemListElement: [
        { '@type': 'ListItem', position: 1, name: t.homeCrumb, item: homeUrl },
        { '@type': 'ListItem', position: 2, name: page.crumb, item: ORIGIN + page.path },
      ],
    }],
  };
  // Split the fragment into its header (h1, date, language link) and body, and build the table of
  // contents from the body's h2[id] (the summary box is not a section).
  const cut = content.search(/<div class="summary">|<h2[\s>]/);
  const docHead = content.slice(0, cut).trim();
  const docBody = content.slice(cut).trim();
  const toc = [...docBody.replace(/<div class="summary">[\s\S]*?<\/div>/, '').matchAll(/<h2 id="([^"]+)">([\s\S]*?)<\/h2>/g)]
    .map(([, id, label]) => `<li><a href="#${id}">${strip(label).replace(/^\d+\.\s*/, '')}</a></li>`).join('');
  return `${head(p)}
<body>
${header(lang, false)}
<main id="main" class="doc">
  <div class="wrap doc-grid">
    <header class="doc-head">
      <nav class="crumbs" aria-label="${t.crumbs}"><ol><li><a href="/">${t.homeCrumb}</a></li><li aria-current="page">${page.crumb}</li></ol></nav>
${docHead}
    </header>
    <aside class="doc-toc">
      <nav aria-labelledby="toc-title"><p class="toc-title" id="toc-title">${t.toc}</p><ol>${toc}</ol></nav>
    </aside>
    <article class="doc-body">
${docBody}
    </article>
  </div>
</main>
${footer(lang)}
</body>
</html>
`;
}

function notFound() {
  const p = { lang: 'vi', path: '/404', canonical: '/', noindex: true, title: 'Không tìm thấy trang | TSPodcast',
    description: 'Trang bạn tìm không tồn tại. Quay lại trang chủ TSPodcast hoặc nghe podcast, radio online ngay trên web.' };
  return `${head(p)}
<body>
${header('vi', false)}
<main id="main" class="notfound">
  <div class="wrap">
    <h1>Không tìm thấy trang</h1>
    <p>Đường dẫn này không tồn tại hoặc đã được chuyển đi.</p>
    <div class="btn-row">
      <a class="btn btn-primary" href="/">Về trang chủ</a>
      <a class="btn btn-outline" id="cta-404-web" data-cta="404-web" href="${esc(webHref('/'))}">Nghe ngay trên web</a>
    </div>
  </div>
</main>
${footer('vi')}
</body>
</html>
`;
}

// ---------------------------------------------------------------- write
function write(file, html) {
  const out = join(OUT, file);
  mkdirSync(dirname(out), { recursive: true });
  writeFileSync(out, html);
  console.log(`${file.padEnd(22)} ${(Buffer.byteLength(html) / 1024).toFixed(1)} KB`);
}

for (const v of LANDINGS) write(v.file, landing(v));
for (const d of DOCS) for (const lang of ['vi', 'en']) write(d[lang].file, doc(d, lang));
write('404.html', notFound());

const urls = [
  ['/', '1.0'],
  ...DOCS.flatMap((d) => [[d.vi.path, '0.3'], [d.en.path, '0.2']]),
];
write('sitemap.xml', `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">
${urls.map(([path, prio]) => {
  const d = DOCS.find((x) => x.vi.path === path || x.en.path === path);
  const alts = d ? [['vi', d.vi.path], ['en', d.en.path], ['x-default', d.vi.path]]
    .map(([hl, p]) => `\n    <xhtml:link rel="alternate" hreflang="${hl}" href="${ORIGIN}${p}"/>`).join('') : '';
  return `  <url>\n    <loc>${ORIGIN}${path}</loc>\n    <lastmod>${UPDATED}</lastmod>\n    <priority>${prio}</priority>${alts}\n  </url>`;
}).join('\n')}
</urlset>
`);

for (const [name, js] of [['campaign', CAMPAIGN_JS], ['reveal', REVEAL_JS]]) {
  if (js.length >= 1024) throw new Error(`${name} script is ${js.length} bytes (limit 1 KB)`);
}
const fb = readFileSync(join(REPO, 'firebase.json'), 'utf8');
console.log(`scripts: campaign ${CAMPAIGN_JS.length} B, reveal ${REVEAL_JS.length} B`);
console.log(`CSP script-src: ${SCRIPT_HASHES.map((h) => `'${h}'`).join(' ')}`);
const missing = SCRIPT_HASHES.filter((h) => !fb.includes(h));
if (missing.length) {
  console.error(`\nWARNING: firebase.json CSP is missing ${missing.join(', ')}. Update script-src or the scripts will be blocked.`);
  process.exitCode = 1;
}
