import { LegalDoc, legalMetadata } from "@/components/LegalDoc";

export const dynamic = "force-static";
export const metadata = legalMetadata("delete-data", "vi");

export default function Page() {
  return <LegalDoc doc="delete-data" lang="vi" />;
}
