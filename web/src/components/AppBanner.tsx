"use client";

import { useState, useSyncExternalStore } from "react";
import { CloseIcon, PhoneIcon } from "@/components/icons";
import { t } from "@/lib/i18n";
import { androidIntentUrl, playStoreUrl } from "@/lib/site";

// docs/plans/seo-web-dashboard.md §A.10: a slim, dismissible banner that
// remembers dismissal for 14 days and never pushes layout (it overlays the top
// of the page, so it cannot cause CLS when it appears after hydration).
const DISMISS_KEY = "tsp.appBanner.dismissedAt";
const DISMISS_FOR_MS = 14 * 24 * 3600 * 1000;

const noopSubscribe = () => () => {};

function isAndroid(): boolean {
  return /Android/i.test(navigator.userAgent);
}

function bannerWanted(): boolean {
  if (!isAndroid()) return false;
  try {
    const at = Number(localStorage.getItem(DISMISS_KEY) ?? 0);
    return !at || Date.now() - at > DISMISS_FOR_MS;
  } catch {
    return true;
  }
}

/** Android-only "smart app banner" (Chrome has no native one for Play apps). */
export function AppBanner() {
  const wanted = useSyncExternalStore(noopSubscribe, bannerWanted, () => false);
  const [dismissed, setDismissed] = useState(false);
  if (!wanted || dismissed) return null;

  const dismiss = () => {
    setDismissed(true);
    try {
      localStorage.setItem(DISMISS_KEY, String(Date.now()));
    } catch {
      // ignore
    }
  };

  return (
    <aside
      aria-label={t.app.bannerTitle}
      className="fixed inset-x-2 top-2 z-50 flex items-center gap-3 rounded-2xl border border-border bg-surface px-3 py-2 text-sm shadow-lg"
    >
      <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-xl bg-primary text-on-primary">
        <PhoneIcon size={18} />
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate font-semibold">{t.app.bannerTitle}</p>
        <p className="truncate text-xs text-subtle">{t.app.bannerBody}</p>
      </div>
      <a
        href={androidIntentUrl(playStoreUrl({ medium: "smart_banner" }))}
        className="shrink-0 rounded-full bg-primary px-3.5 py-1.5 text-xs font-bold text-on-primary"
      >
        {t.app.openInApp}
      </a>
      <button
        type="button"
        onClick={dismiss}
        aria-label={t.app.dismiss}
        className="inline-flex size-8 shrink-0 items-center justify-center rounded-full text-subtle hover:bg-surface-2"
      >
        <CloseIcon size={14} />
      </button>
    </aside>
  );
}

/**
 * "Open in app" for content pages: launches the app on Android (falling back to
 * the Play Store with an install referrer pointing at this podcast), otherwise
 * links to the Play Store.
 */
export function OpenInAppLink({ feedId, className = "" }: { feedId?: number; className?: string }) {
  const android = useSyncExternalStore(noopSubscribe, isAndroid, () => false);
  const store = playStoreUrl({
    medium: "open_in_app",
    content: feedId ? `podcast_${feedId}` : undefined,
    deeplink: feedId ? `/podcast/${feedId}` : undefined,
  });
  return (
    <a
      href={android ? androidIntentUrl(store) : store}
      rel="noopener"
      target={android ? undefined : "_blank"}
      className={`inline-flex items-center gap-2 rounded-full border border-border bg-surface px-4 py-2 text-sm font-semibold transition hover:border-primary hover:text-primary ${className}`}
    >
      <PhoneIcon size={16} />
      {android ? t.app.openInApp : t.app.getApp}
    </a>
  );
}
