import {
  aiDisabledResponse,
  badRequest,
  isAiEnabled,
  logAiError,
  rateLimit,
  rateLimitedResponse,
} from "@/lib/ai";
import { runAiSearch } from "@/lib/ai-search";
import { isPodcastIndexConfigured } from "@/lib/podcastindex";
import { t } from "@/lib/i18n";

export const runtime = "nodejs";
export const maxDuration = 60;

export async function POST(request: Request) {
  if (!isAiEnabled()) return aiDisabledResponse();
  if (!isPodcastIndexConfigured()) {
    return Response.json({ error: "missing_keys", message: t.setup.title }, { status: 503 });
  }
  if (!rateLimit(request, "ai-search", 15)) return rateLimitedResponse();

  let query: string;
  try {
    const body = (await request.json()) as { query?: unknown };
    query = typeof body.query === "string" ? body.query.trim() : "";
  } catch {
    return badRequest("invalid JSON");
  }
  if (query.length < 2 || query.length > 300) return badRequest("query must be 2-300 characters");

  try {
    const result = await runAiSearch(query);
    if (!result) return Response.json({ error: "refused", message: t.ai.error }, { status: 422 });
    return Response.json(result, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    logAiError("search", error);
    return Response.json({ error: "upstream", message: t.ai.error }, { status: 502 });
  }
}
