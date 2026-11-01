import { ANDROID_PACKAGE } from "@/lib/site";

// Android App Links verification (docs/in-progress/plans/seo-web-dashboard.md §A.10). Set
// ANDROID_CERT_SHA256 to the Play App Signing key fingerprint, plus the upload
// key for internal builds, comma-separated ("AA:BB:..."). The app still needs
// an autoVerify VIEW intent-filter for these URLs before links open in it.
// Cached for an hour; a static export writes it once at build time.
export const revalidate = 3600;

export function GET() {
  const fingerprints = (process.env.ANDROID_CERT_SHA256 ?? "")
    .split(",")
    .map((s) => s.trim().toUpperCase())
    .filter((s) => /^([0-9A-F]{2}:){31}[0-9A-F]{2}$/.test(s));
  if (fingerprints.length === 0) {
    return Response.json([], { status: 404 });
  }
  return Response.json(
    [
      {
        relation: ["delegate_permission/common.handle_all_urls"],
        target: {
          namespace: "android_app",
          package_name: ANDROID_PACKAGE,
          sha256_cert_fingerprints: fingerprints,
        },
      },
    ],
    { headers: { "Cache-Control": "public, max-age=3600" } },
  );
}
