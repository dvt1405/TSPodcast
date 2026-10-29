import Link from "next/link";
import { t } from "@/lib/i18n";
import { PODCASTINDEX_URL } from "@/lib/site";

/** Shown instead of content when PODCASTINDEX_API_KEY / _SECRET are missing. */
export function SetupNotice() {
  return (
    <section
      role="status"
      aria-labelledby="setup-title"
      className="rounded-2xl border border-accent/40 bg-surface p-5 shadow-sm sm:p-6"
    >
      <h2 id="setup-title" className="text-lg font-bold">
        {t.setup.title}
      </h2>
      <p className="mt-2 text-sm text-muted">{t.setup.body}</p>
      <pre className="mt-3 overflow-x-auto rounded-lg bg-surface-2 p-3 text-xs leading-relaxed">
        <code>{`PODCASTINDEX_API_KEY=...\nPODCASTINDEX_API_SECRET=...\n# tuỳ chọn / optional\nANTHROPIC_API_KEY=...`}</code>
      </pre>
      <p className="mt-3 text-sm text-muted">
        {t.setup.steps}{" "}
        <a href="https://api.podcastindex.org/" className="font-semibold text-primary underline" rel="noopener" target="_blank">
          api.podcastindex.org
        </a>
        . {t.setup.note}
      </p>
    </section>
  );
}

export function ErrorNotice({ message = t.errors.loadFailed }: { message?: string }) {
  return (
    <p role="alert" className="rounded-xl border border-danger/30 bg-surface p-4 text-sm text-danger">
      {message}
    </p>
  );
}

/** Renders the "keys missing" or "load failed" state of a `load()` result. */
export function LoadFailure({ reason }: { reason: "missing-keys" | "error" }) {
  return reason === "missing-keys" ? <SetupNotice /> : <ErrorNotice />;
}

export function EmptyState({ message }: { message: string }) {
  return <p className="rounded-xl bg-surface p-6 text-center text-sm text-subtle">{message}</p>;
}

export function PodcastIndexCredit({ className = "" }: { className?: string }) {
  return (
    <p className={`text-xs text-subtle ${className}`}>
      <Link href={PODCASTINDEX_URL} className="underline hover:text-primary" rel="noopener" target="_blank">
        {t.footer.attribution}
      </Link>
    </p>
  );
}
