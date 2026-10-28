"use client";

import { MoonIcon, SunIcon } from "@/components/icons";
import { t } from "@/lib/i18n";

export const THEME_KEY = "tsp.theme";

/** Runs in <head> before paint so there is no light/dark flash. */
export const themeInitScript = `(function(){try{var s=localStorage.getItem("${THEME_KEY}");var d=s?s==="dark":window.matchMedia("(prefers-color-scheme: dark)").matches;document.documentElement.classList.toggle("dark",d);}catch(e){}})();`;

export function ThemeToggle() {
  const toggle = () => {
    const root = document.documentElement;
    const dark = !root.classList.contains("dark");
    root.classList.toggle("dark", dark);
    try {
      localStorage.setItem(THEME_KEY, dark ? "dark" : "light");
    } catch {
      // Storage disabled: the choice lasts for this page view only.
    }
  };
  // Both icons are rendered; CSS picks one, so server and client markup match.
  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={t.theme.toggle}
      title={t.theme.toggle}
      className="inline-flex size-10 items-center justify-center rounded-full text-muted transition hover:bg-surface-2 hover:text-foreground"
    >
      <MoonIcon size={20} className="dark:hidden" />
      <SunIcon size={20} className="hidden dark:block" />
    </button>
  );
}
