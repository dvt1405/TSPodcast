import { readFileSync } from "node:fs";
import { join } from "node:path";
import type { Metadata } from "next";

// Privacy policy, terms and data deletion: the HTML comes from src/legal/ (synced from
// site/_src/content by scripts/sync-legal.mjs), our own text, rendered at build time.

export type LegalDocName = "privacy" | "terms" | "delete-data";
export type LegalLang = "vi" | "en";

const META: Record<LegalDocName, Record<LegalLang, { title: string; description: string }>> = {
  privacy: {
    vi: { title: "Chính sách quyền riêng tư", description: "Dữ liệu TSPodcast xử lý, lý do, bên nhận, thời gian lưu trữ và quyền của bạn. Không cần tài khoản." },
    en: { title: "Privacy Policy", description: "What data TSPodcast processes, why, who receives it, how long it is kept and your rights. No account needed." },
  },
  terms: {
    vi: { title: "Điều khoản sử dụng", description: "Điều khoản sử dụng ứng dụng và trang web TSPodcast, gồm gói Premium." },
    en: { title: "Terms of Service", description: "Terms for using the TSPodcast apps and website, including Premium." },
  },
  "delete-data": {
    vi: { title: "Xoá dữ liệu", description: "Cách xoá dữ liệu TSPodcast trên thiết bị và yêu cầu xoá dữ liệu do bên thứ ba lưu." },
    en: { title: "Delete your data", description: "How to delete TSPodcast data on your device and request deletion of data held by third parties." },
  },
};

const path = (doc: LegalDocName, lang: LegalLang) => (lang === "en" ? `/en/${doc}` : `/${doc}`);

export function legalMetadata(doc: LegalDocName, lang: LegalLang): Metadata {
  const { title, description } = META[doc][lang];
  return {
    title,
    description,
    alternates: { canonical: path(doc, lang), languages: { vi: path(doc, "vi"), en: path(doc, "en") } },
    openGraph: { title, description, url: path(doc, lang) },
  };
}

export function LegalDoc({ doc, lang }: { doc: LegalDocName; lang: LegalLang }) {
  const html = readFileSync(join(process.cwd(), "src/legal", `${doc}.${lang}.html`), "utf8");
  return (
    <article lang={lang} className="legal-doc mx-auto max-w-3xl px-4 py-8" dangerouslySetInnerHTML={{ __html: html }} />
  );
}
