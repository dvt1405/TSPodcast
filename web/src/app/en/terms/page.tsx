import { LegalDoc, legalMetadata } from "@/components/LegalDoc";

export const dynamic = "force-static";
export const metadata = legalMetadata("terms", "en");

export default function Page() {
  return <LegalDoc doc="terms" lang="en" />;
}
