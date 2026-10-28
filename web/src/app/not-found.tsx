import Link from "next/link";
import { t } from "@/lib/i18n";

export default function NotFound() {
  return (
    <div className="mx-auto max-w-md py-16 text-center">
      <h1 className="text-2xl font-bold">{t.errors.notFoundTitle}</h1>
      <p className="mt-2 text-muted">{t.errors.notFoundBody}</p>
      <Link
        href="/"
        className="mt-6 inline-block rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-on-primary"
      >
        {t.errors.backHome}
      </Link>
    </div>
  );
}
