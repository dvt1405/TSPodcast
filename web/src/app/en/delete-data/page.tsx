import { LegalDoc, legalMetadata } from "@/components/LegalDoc";

export const dynamic = "force-static";
export const metadata = legalMetadata("delete-data", "en");

export default function Page() {
  return <LegalDoc doc="delete-data" lang="en" />;
}
