import { t } from "@/lib/i18n";

export default function Loading() {
  return (
    <div aria-busy="true" aria-live="polite" className="animate-pulse space-y-6 py-2">
      <span className="sr-only">{t.common.loading}</span>
      <div className="h-7 w-48 rounded-lg bg-surface-2" />
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-6">
        {Array.from({ length: 6 }, (_, i) => (
          <div key={i} className="aspect-square rounded-xl bg-surface-2" />
        ))}
      </div>
      <div className="space-y-3">
        {Array.from({ length: 4 }, (_, i) => (
          <div key={i} className="h-16 rounded-xl bg-surface-2" />
        ))}
      </div>
    </div>
  );
}
