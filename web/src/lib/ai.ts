import "server-only";
import Anthropic from "@anthropic-ai/sdk";
import type { BetaMessageStreamParams } from "@anthropic-ai/sdk/resources/beta/messages/messages";
import { locale, t } from "./i18n";
import { STATIC_EXPORT } from "./site";

/** Model requested for this project. Override with ANTHROPIC_MODEL. */
export const AI_MODEL = process.env.ANTHROPIC_MODEL?.trim() || "claude-sonnet-5-5";

/**
 * AI is opt-in: without ANTHROPIC_API_KEY every AI route answers 503 and the
 * UI hides the AI panels. (The SDK could also pick up an `ant auth login`
 * profile, but a deployed site should not depend on that implicitly.)
 */
export function isAiEnabled(): boolean {
  if (STATIC_EXPORT) return false; // no server for /api/ai/* in a static export
  return Boolean(process.env.ANTHROPIC_API_KEY?.trim());
}

let client: Anthropic | null = null;
export function getAnthropic(): Anthropic {
  client ??= new Anthropic({ apiKey: process.env.ANTHROPIC_API_KEY, maxRetries: 2, timeout: 120_000 });
  return client;
}

/**
 * Server-side refusal fallback ("default" routing by refusal category). Claude
 * API only; set ANTHROPIC_FALLBACKS=off when pointing ANTHROPIC_BASE_URL at a
 * gateway that rejects the beta.
 */
export function fallbackParams(): Pick<BetaMessageStreamParams, "betas" | "fallbacks"> {
  if (process.env.ANTHROPIC_FALLBACKS === "off") return {};
  return { betas: ["server-side-fallback-2026-07-01"], fallbacks: "default" };
}

export const replyLanguage = locale === "vi" ? "Vietnamese (tiếng Việt)" : "English";

// ---------- tiny in-process LRU (per server instance) ----------

export class LruCache<V> {
  private map = new Map<string, { value: V; expires: number }>();
  constructor(
    private readonly max: number,
    private readonly ttlMs: number,
  ) {}
  get(key: string): V | undefined {
    const hit = this.map.get(key);
    if (!hit) return undefined;
    if (hit.expires < Date.now()) {
      this.map.delete(key);
      return undefined;
    }
    this.map.delete(key);
    this.map.set(key, hit);
    return hit.value;
  }
  set(key: string, value: V): void {
    this.map.delete(key);
    this.map.set(key, { value, expires: Date.now() + this.ttlMs });
    while (this.map.size > this.max) {
      const oldest = this.map.keys().next().value;
      if (oldest === undefined) break;
      this.map.delete(oldest);
    }
  }
}

// ---------- rate limiting (fixed window, per IP, per instance) ----------

const windows = new Map<string, { count: number; reset: number }>();

export function rateLimit(request: Request, bucket: string, limit: number, windowMs = 60_000): boolean {
  const ip =
    request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ||
    request.headers.get("x-real-ip") ||
    "local";
  const key = `${bucket}:${ip}`;
  const now = Date.now();
  const entry = windows.get(key);
  if (!entry || entry.reset < now) {
    windows.set(key, { count: 1, reset: now + windowMs });
    if (windows.size > 10_000) {
      for (const [k, v] of windows) if (v.reset < now) windows.delete(k);
    }
    return true;
  }
  entry.count += 1;
  return entry.count <= limit;
}

// ---------- responses ----------

export function aiDisabledResponse(): Response {
  return Response.json({ error: "ai_disabled", message: t.ai.disabled }, { status: 503 });
}

export function rateLimitedResponse(): Response {
  return Response.json({ error: "rate_limited", message: t.ai.rateLimited }, { status: 429 });
}

export function badRequest(message: string): Response {
  return Response.json({ error: "bad_request", message }, { status: 400 });
}

export function logAiError(scope: string, error: unknown): void {
  if (error instanceof Anthropic.APIError) {
    console.error(`[ai:${scope}] API ${error.status}: ${error.message}`);
  } else {
    console.error(`[ai:${scope}]`, error instanceof Error ? error.message : error);
  }
}

const REFUSAL_NOTE =
  locale === "vi"
    ? "\n\n_(AI từ chối trả lời yêu cầu này.)_"
    : "\n\n_(The AI declined to answer this request.)_";

/**
 * Streams the text of a Messages API call as a plain-text HTTP body.
 * `onComplete` receives the full text after a normal finish (used for caching).
 */
export function streamTextResponse(
  params: BetaMessageStreamParams,
  scope: string,
  onComplete?: (text: string) => void,
): Response {
  const encoder = new TextEncoder();
  const body = new ReadableStream<Uint8Array>({
    async start(controller) {
      let full = "";
      try {
        const stream = getAnthropic().beta.messages.stream(params);
        stream.on("text", (delta) => {
          full += delta;
          controller.enqueue(encoder.encode(delta));
        });
        const message = await stream.finalMessage();
        if (message.stop_reason === "refusal") {
          controller.enqueue(encoder.encode(REFUSAL_NOTE));
        } else if (full.trim()) {
          onComplete?.(full);
        }
        controller.close();
      } catch (error) {
        logAiError(scope, error);
        controller.error(error);
      }
    },
  });
  return new Response(body, {
    headers: {
      "Content-Type": "text/plain; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Accel-Buffering": "no",
    },
  });
}

export function textResponse(text: string, cacheHit: boolean): Response {
  return new Response(text, {
    headers: {
      "Content-Type": "text/plain; charset=utf-8",
      "Cache-Control": "no-store",
      "X-AI-Cache": cacheHit ? "hit" : "miss",
    },
  });
}
