import Link from "next/link";
import { NavLinks } from "@/components/NavLinks";
import { SearchIcon } from "@/components/icons";
import { ThemeToggle } from "@/components/ThemeToggle";
import { t } from "@/lib/i18n";
import { SITE_NAME, STATIC_EXPORT } from "@/lib/site";

export function Logo() {
  return (
    <Link href="/" className="group flex shrink-0 items-center gap-2 font-bold tracking-tight" aria-label={SITE_NAME}>
      {/* eslint-disable-next-line @next/next/no-img-element -- tiny static SVG brand mark (brand/web/favicon.svg) */}
      <img src="/favicon.svg" alt="" width={32} height={32} className="size-8 rounded-xl shadow-sm transition-transform duration-300 group-hover:-rotate-6 group-hover:scale-105" />
      <span className="text-lg">
        TS<span className="text-primary">Podcast</span>
      </span>
    </Link>
  );
}

export function Header() {
  return (
    <header className="site-header sticky top-0 z-30 border-b border-border bg-background/90 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-6xl items-center gap-3 px-4">
        <Logo />
        <nav aria-label={t.nav.mainNav} className="ml-4 hidden md:block">
          <NavLinks />
        </nav>
        {STATIC_EXPORT ? (
          <div className="ml-auto" />
        ) : (
          <>
            <form action="/search" role="search" className="ml-auto hidden max-w-xs flex-1 sm:block">
              <label htmlFor="header-search" className="sr-only">
                {t.search.label}
              </label>
              <div className="flex items-center gap-2 rounded-full border border-border bg-surface px-3 focus-within:border-primary">
                <SearchIcon size={16} className="text-subtle" />
                <input
                  id="header-search"
                  name="q"
                  type="search"
                  placeholder={t.search.placeholder}
                  className="h-9 w-full bg-transparent text-sm outline-none placeholder:text-subtle"
                />
              </div>
            </form>
            <Link
              href="/search"
              aria-label={t.nav.search}
              className="ml-auto inline-flex size-10 items-center justify-center rounded-full text-muted hover:bg-surface-2 sm:hidden"
            >
              <SearchIcon size={20} />
            </Link>
          </>
        )}
        <ThemeToggle />
      </div>
      <nav aria-label={t.nav.mainNav} className="border-t border-border/60 px-4 md:hidden">
        <NavLinks compact />
      </nav>
    </header>
  );
}
