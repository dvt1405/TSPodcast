// /app-ads.txt for AdMob verification (seo-web-dashboard.md §A.9). The exact
// line comes from the AdMob console, e.g.
//   APP_ADS_TXT="google.com, pub-0000000000000000, DIRECT, f08c47fec0942fa0"
// Several lines may be separated by "\n". Served with 200 and no redirect; this
// domain must match the "Website" field of the Play listing.
// Cached for an hour; a static export writes it once at build time.
export const revalidate = 3600;

export function GET() {
  const body = process.env.APP_ADS_TXT?.replace(/\\n/g, "\n").trim();
  if (!body) {
    return new Response("app-ads.txt is not configured\n", {
      status: 404,
      headers: { "Content-Type": "text/plain; charset=utf-8" },
    });
  }
  return new Response(`${body}\n`, {
    headers: { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "public, max-age=3600" },
  });
}
