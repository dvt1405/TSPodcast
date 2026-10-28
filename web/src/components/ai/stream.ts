"use client";

import { t } from "@/lib/i18n";

export class AiRequestError extends Error {}

/** POSTs JSON and feeds the plain-text streamed body to `onText` as it arrives. */
export async function streamPost(
  url: string,
  body: unknown,
  onText: (full: string) => void,
  signal?: AbortSignal,
): Promise<string> {
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
    signal,
  });
  if (!res.ok || !res.body) {
    let message = t.ai.error;
    try {
      const data = (await res.json()) as { message?: string; error?: string };
      if (res.status === 429 || res.status === 503) message = data.message ?? message;
    } catch {
      // non-JSON error body
    }
    throw new AiRequestError(message);
  }
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let full = "";
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    full += decoder.decode(value, { stream: true });
    onText(full);
  }
  full += decoder.decode();
  onText(full);
  return full;
}
