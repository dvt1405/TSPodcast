// /app-ads.txt for AdMob verification. APP_ADS_TXT can override the publisher
// record below; several lines may be separated by "\n". This domain must match
// the "Website" field of the Play listing.
// Cached for an hour; a static export writes it once at build time.
export const dynamic = "force-static";
export const revalidate = 3600;

export function GET() {
  const body = process.env.APP_ADS_TXT?.replace(/\\n/g, "\n").trim()
    || "google.com, pub-8696388065064767, DIRECT, f08c47fec0942fa0";
  return new Response(`${body}\n`, {
    headers: { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "public, max-age=3600" },
  });
}
