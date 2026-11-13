import Link from "next/link";
import { t } from "@/lib/i18n";
import { IOS_TESTFLIGHT_URL, PODCASTINDEX_URL, SITE_NAME, STATIC_EXPORT, playStoreUrl } from "@/lib/site";

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
          {!STATIC_EXPORT && (
            <Link href="/search" className="hover:text-primary">
              {t.nav.search}
            </Link>
          )}
          <Link href="/privacy" className="hover:text-primary">
            Chính sách quyền riêng tư
          </Link>
          <Link href="/terms" className="hover:text-primary">
            Điều khoản sử dụng
          </Link>
          <Link href="/delete-data" className="hover:text-primary">
            Xoá dữ liệu
          </Link>
          <a href="mailto:admin@tuandv.me" className="hover:text-primary">
            Liên hệ hỗ trợ
          </a>
          <a href={playStoreUrl({ medium: "footer" })} rel="noopener" target="_blank" className="hover:text-primary">
            {t.app.getApp} (Android)
          </a>
          <a href={IOS_TESTFLIGHT_URL} rel="noopener" target="_blank" className="hover:text-primary">
            {t.app.getApp} {t.app.iosBeta}
          </a>
        </nav>
        <p>© {SITE_NAME}</p>
      </div>
    </footer>
  );
}
