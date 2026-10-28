import Link from "next/link";
import { NavLinks } from "@/components/NavLinks";
import { SearchIcon } from "@/components/icons";
import { ThemeToggle } from "@/components/ThemeToggle";
import { t } from "@/lib/i18n";
import { SITE_NAME } from "@/lib/site";

export function Logo() {
  return (
    <Link href="/" className="flex shrink-0 items-center gap-2 font-bold tracking-tight" aria-label={SITE_NAME}>
      <span className="inline-flex size-8 items-center justify-center rounded-xl bg-gradient-to-br from-primary to-secondary text-white shadow-sm">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth={2.2} aria-hidden="true">
          <rect x="9" y="3" width="6" height="11" rx="3" />
          <path d="M5 11a7 7 0 0 0 14 0M12 18v3" strokeLinecap="round" />
        </svg>
      </span>
      <span className="text-lg">
        TS<span className="text-primary">Podcast</span>
      </span>
    </Link>
  );
}

export function Header() {
  return (
    <header className="sticky top-0 z-30 border-b border-border bg-background/90 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-6xl items-center gap-3 px-4">
        <Logo />
        <nav aria-label={t.nav.mainNav} className="ml-4 hidden md:block">
          <NavLinks />
        </nav>
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
        <ThemeToggle />
      </div>
      <nav aria-label={t.nav.mainNav} className="border-t border-border/60 px-4 md:hidden">
        <NavLinks compact />
      </nav>
    </header>
  );
}
