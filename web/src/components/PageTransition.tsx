"use client";

import { usePathname } from "next/navigation";
import { ViewTransition } from "react";

/**
 * Crossfades the page on route changes (the `.page` view-transition class in
 * globals.css). Keyed by pathname so a navigation is an exit/enter pair, while
 * updates inside a page (playing an episode, typing a search, loading more) do
 * not animate the whole page. The header and player have their own
 * view-transition names and stay still.
 */
export function PageTransition({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  return (
    <ViewTransition key={pathname} enter="page" exit="page" update="none" default="none">
      {children}
    </ViewTransition>
  );
}
