import { LegalDoc, legalMetadata } from "@/components/LegalDoc";

export const dynamic = "force-static";
export const metadata = legalMetadata("privacy", "vi");

export default function Page() {
  return <LegalDoc doc="privacy" lang="vi" />;
}
