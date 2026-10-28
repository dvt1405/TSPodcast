import Link from "next/link";
import { t } from "@/lib/i18n";
import { PODCASTINDEX_URL, SITE_NAME, playStoreUrl } from "@/lib/site";

export function Footer({ aiEnabled }: { aiEnabled: boolean }) {
  return (
    <footer className="mt-12 border-t border-border bg-surface">
      <div className="mx-auto max-w-6xl space-y-3 px-4 py-8 text-sm text-subtle">
        {/* Podcast Index terms 7.2: attribution must be displayed. */}
        <p>
          <a href={PODCASTINDEX_URL} rel="noopener" target="_blank" className="font-semibold text-muted underline hover:text-primary">
            {t.footer.attribution}
          </a>
          {" · "}
          {t.footer.radioAttribution}
          {aiEnabled && ` · ${t.footer.aiAttribution}`}
        </p>
        <nav aria-label="Footer" className="flex flex-wrap gap-x-4 gap-y-1">
          <Link href="/trending" className="hover:text-primary">
            {t.nav.trending}
          </Link>
          <Link href="/radio" className="hover:text-primary">
            {t.nav.radio}
          </Link>
          <Link href="/search" className="hover:text-primary">
            {t.nav.search}
          </Link>
          <a href={playStoreUrl({ medium: "footer" })} rel="noopener" target="_blank" className="hover:text-primary">
            {t.app.getApp} (Android)
          </a>
        </nav>
        <p>© {SITE_NAME}</p>
      </div>
    </footer>
  );
}
