"use client";

import { useEffect, useState } from "react";
import { EpisodeList, PodcastGrid, SectionHeader } from "@/components/cards";
import { SparkIcon } from "@/components/icons";
import { t } from "@/lib/i18n";
import type { Episode, PodcastSummary } from "@/lib/types";

interface Result {
  intent: {
    keywords: string[];
    medium: "podcast" | "music";
    language: string;
    maxDurationMinutes: number | null;
    minDurationMinutes: number | null;
    understood: string;
  };
  podcasts: PodcastSummary[];
  episodes: Episode[];
}

type State =
  | { status: "loading" }
  | { status: "done"; result: Result }
  | { status: "error"; message: string };

/** Runs AI search for `query` (from the URL) and renders the results. */
export function AiSearch({ query }: { query: string }) {
  const [state, setState] = useState<State>({ status: "loading" });
  const [prevQuery, setPrevQuery] = useState(query);
  if (prevQuery !== query) {
    // Reset during render when the query changes (no effect-driven setState).
    setPrevQuery(query);
    setState({ status: "loading" });
  }

  useEffect(() => {
    const controller = new AbortController();
    fetch("/api/ai/search", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ query }),
      signal: controller.signal,
    })
      .then(async (res) => {
        const data = (await res.json()) as Result & { message?: string };
        if (!res.ok) throw new Error(data.message || t.ai.error);
        setState({ status: "done", result: data });
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        setState({ status: "error", message: error instanceof Error ? error.message : t.ai.error });
      });
    return () => controller.abort();
  }, [query]);

  if (state.status === "loading") {
    return (
      <p aria-live="polite" className="flex animate-pulse items-center gap-2 text-sm text-subtle">
        <SparkIcon size={16} /> {t.ai.thinking}
      </p>
    );
  }
  if (state.status === "error") {
    return (
      <p role="alert" className="text-sm text-danger">
        {state.message}
      </p>
    );
  }

  const { intent, podcasts, episodes } = state.result;
  const chips = [
    ...intent.keywords.map((k) => `“${k}”`),
    intent.medium === "music" ? t.search.modeMusic : "",
    intent.maxDurationMinutes ? t.ai.searchMaxDuration(intent.maxDurationMinutes) : "",
    intent.minDurationMinutes ? t.ai.searchMinDuration(intent.minDurationMinutes) : "",
    intent.language !== "any" ? intent.language.toUpperCase() : "",
  ].filter(Boolean);

  return (
    <div className="space-y-8" aria-live="polite">
      <div className="rounded-2xl border border-primary/25 bg-surface p-4">
        <p className="flex items-center gap-2 text-sm font-semibold">
          <SparkIcon size={16} className="text-primary" />
          {t.ai.searchUnderstood}: <span className="font-normal">{intent.understood}</span>
        </p>
        <ul className="mt-2 flex flex-wrap gap-1.5" aria-label={t.ai.searchKeywords}>
          {chips.map((c) => (
            <li key={c} className="rounded-full bg-primary-soft px-2.5 py-0.5 text-xs font-medium text-primary">
              {c}
            </li>
          ))}
        </ul>
      </div>

      {episodes.length > 0 && (
        <section aria-labelledby="ai-episodes">
          <SectionHeader id="ai-episodes" title={t.ai.matchingEpisodes} />
          <EpisodeList episodes={episodes} />
        </section>
      )}

      <section aria-labelledby="ai-podcasts">
        <SectionHeader id="ai-podcasts" title={t.ai.matchingPodcasts} />
        {podcasts.length ? (
          <PodcastGrid podcasts={podcasts} />
        ) : (
          <p className="text-sm text-subtle">{t.search.empty}</p>
        )}
      </section>
    </div>
  );
}
