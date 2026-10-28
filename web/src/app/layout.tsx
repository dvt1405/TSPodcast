import type { Metadata, Viewport } from "next";
import { Open_Sans } from "next/font/google";
import { AppBanner } from "@/components/AppBanner";
import { Footer } from "@/components/Footer";
import { Header } from "@/components/Header";
import { PlayerBar } from "@/components/player/PlayerBar";
import { themeInitScript } from "@/components/ThemeToggle";
import { isAiEnabled } from "@/lib/ai";
import { htmlLang, ogLocale, t } from "@/lib/i18n";
import { JsonLd } from "@/components/JsonLd";
import { ANDROID_PACKAGE, OG_IMAGES, PLAY_STORE_URL, SITE_NAME, SITE_URL } from "@/lib/site";
import "./globals.css";

// The Android app's typeface (sharedLibrary Fonts.openSans), with the
// Vietnamese subset for diacritics.
const openSans = Open_Sans({
  variable: "--font-open-sans",
  subsets: ["latin", "vietnamese"],
  display: "swap",
});

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: { default: `${SITE_NAME} — ${t.meta.tagline}`, template: `%s · ${SITE_NAME}` },
  description: t.meta.description,
  applicationName: SITE_NAME,
  // Icons and the share image come from brand/web (TSP-15), copied to public/.
  icons: {
    icon: [
      { url: "/favicon.ico", sizes: "any" },
      { url: "/favicon.svg", type: "image/svg+xml" },
      { url: "/favicon-32.png", sizes: "32x32", type: "image/png" },
      { url: "/favicon-16.png", sizes: "16x16", type: "image/png" },
    ],
    apple: [{ url: "/apple-touch-icon.png", sizes: "180x180" }],
  },
  openGraph: {
    type: "website",
    siteName: SITE_NAME,
    locale: ogLocale,
    title: `${SITE_NAME} — ${t.meta.tagline}`,
    description: t.meta.description,
    images: OG_IMAGES,
  },
  twitter: { card: "summary_large_image", images: ["/og-image.png"] },
  appLinks: { android: { package: ANDROID_PACKAGE, app_name: SITE_NAME } },
  other: { "google-play-app": `app-id=${ANDROID_PACKAGE}` },
  formatDetection: { telephone: false },
  verification: process.env.GOOGLE_SITE_VERIFICATION
    ? { google: process.env.GOOGLE_SITE_VERIFICATION }
    : undefined,
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#184BB4" },
    { media: "(prefers-color-scheme: dark)", color: "#121619" },
  ],
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang={htmlLang} className={`${openSans.variable} h-full antialiased`} suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeInitScript }} />
      </head>
      <body className="flex min-h-full flex-col">
        <a
          href="#main"
          className="sr-only z-50 rounded-md bg-primary px-4 py-2 text-on-primary focus:not-sr-only focus:fixed focus:top-2 focus:left-2"
        >
          {t.nav.skipToContent}
        </a>
        <JsonLd
          data={{
            "@context": "https://schema.org",
            "@graph": [
              {
                "@type": "Organization",
                "@id": `${SITE_URL}/#organization`,
                name: SITE_NAME,
                url: SITE_URL,
                logo: `${SITE_URL}/icon-512.png`,
                sameAs: [PLAY_STORE_URL],
              },
              {
                "@type": "WebSite",
                "@id": `${SITE_URL}/#website`,
                name: SITE_NAME,
                url: SITE_URL,
                inLanguage: htmlLang,
                publisher: { "@id": `${SITE_URL}/#organization` },
              },
            ],
          }}
        />
        <AppBanner />
        <Header />
        <main id="main" className="mx-auto w-full max-w-6xl flex-1 px-4 pt-5">
          {children}
        </main>
        <Footer aiEnabled={isAiEnabled()} />
        <PlayerBar />
      </body>
    </html>
  );
}
