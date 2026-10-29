"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { HomeIcon, RadioIcon, SearchIcon, TrendIcon } from "@/components/icons";
import { t } from "@/lib/i18n";

const LINKS = [
  { href: "/", label: t.nav.home, Icon: HomeIcon },
  { href: "/trending", label: t.nav.trending, Icon: TrendIcon },
  { href: "/radio", label: t.nav.radio, Icon: RadioIcon },
  { href: "/search", label: t.nav.search, Icon: SearchIcon },
];

export function NavLinks({ compact = false }: { compact?: boolean }) {
  const pathname = usePathname();
  return (
    <ul className={compact ? "flex justify-between" : "flex items-center gap-1"}>
      {LINKS.map(({ href, label, Icon }) => {
        const active = href === "/" ? pathname === "/" : pathname.startsWith(href);
        return (
          <li key={href}>
            <Link
              href={href}
              aria-current={active ? "page" : undefined}
              className={
                compact
                  ? `flex items-center gap-1.5 py-2 text-xs font-semibold ${active ? "text-primary" : "text-muted"}`
                  : `rounded-full px-3 py-1.5 text-sm font-semibold transition ${
                      active ? "bg-primary-soft text-primary" : "text-muted hover:text-foreground"
                    }`
              }
            >
              {compact && <Icon size={16} />}
              {label}
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
