"use client";

import { useEffect } from "react";
import { t } from "@/lib/i18n";

export default function Error({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <div className="mx-auto max-w-md py-16 text-center" role="alert">
      <h1 className="text-xl font-bold">{t.errors.loadFailed}</h1>
      <button
        type="button"
        onClick={reset}
        className="mt-6 rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-on-primary"
      >
        {t.errors.retry}
      </button>
    </div>
  );
}
