"use client";

import { useRef, useState } from "react";
import { SparkIcon } from "@/components/icons";
import { Markdown } from "@/components/Markdown";
import { t } from "@/lib/i18n";
import { AiRequestError, streamPost } from "./stream";

interface Turn {
  role: "user" | "assistant";
  content: string;
}

function Disclaimer({ hasTranscript }: { hasTranscript: boolean }) {
  return (
    <p className="mt-3 text-[11px] text-subtle">
      {t.ai.disclaimer}
      {hasTranscript ? t.ai.disclaimerTranscript : ""}
      {t.ai.disclaimerEnd}
    </p>
  );
}

function Summary({ episodeId, hasTranscript }: { episodeId: number; hasTranscript: boolean }) {
  const [text, setText] = useState("");
  const [status, setStatus] = useState<"idle" | "loading" | "done" | "error">("idle");
  const [error, setError] = useState("");

  const run = async () => {
    setStatus("loading");
    setText("");
    setError("");
    try {
      await streamPost("/api/ai/summary", { episodeId }, setText);
      setStatus("done");
    } catch (e) {
      setError(e instanceof AiRequestError ? e.message : t.ai.error);
      setStatus("error");
    }
  };

  return (
    <div>
      <div className="flex items-center justify-between gap-3">
        <h2 id="ai-summary-title" className="flex items-center gap-2 font-bold">
          <SparkIcon size={18} className="text-primary" />
          {t.ai.summaryTitle}
        </h2>
        {status !== "loading" && (
          <button
            type="button"
            onClick={run}
            className="rounded-full bg-primary px-4 py-1.5 text-sm font-semibold text-on-primary hover:opacity-90"
          >
            {status === "idle" ? t.ai.summarize : t.ai.regenerate}
          </button>
        )}
      </div>
      <div aria-live="polite" aria-busy={status === "loading"} className="mt-2">
        {status === "loading" && !text && <p className="animate-pulse text-sm text-subtle">{t.ai.summarizing}</p>}
        {text && <Markdown text={text} />}
        {status === "error" && (
          <p role="alert" className="text-sm text-danger">
            {error}
          </p>
        )}
      </div>
      {status === "done" && <Disclaimer hasTranscript={hasTranscript} />}
    </div>
  );
}

function Chat({ episodeId }: { episodeId: number }) {
  const [turns, setTurns] = useState<Turn[]>([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const listRef = useRef<HTMLOListElement>(null);

  const send = async (e: React.FormEvent) => {
    e.preventDefault();
    const question = input.trim();
    if (!question || busy) return;
    const history: Turn[] = [...turns, { role: "user", content: question }];
    setTurns([...history, { role: "assistant", content: "" }]);
    setInput("");
    setBusy(true);
    setError("");
    try {
      await streamPost("/api/ai/chat", { episodeId, messages: history }, (full) => {
        setTurns([...history, { role: "assistant", content: full }]);
        listRef.current?.lastElementChild?.scrollIntoView({ block: "nearest" });
      });
    } catch (err) {
      // Drop the failed exchange so the history stays user/assistant alternating.
      setTurns(turns);
      setInput(question);
      setError(err instanceof AiRequestError ? err.message : t.ai.error);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <h2 className="font-bold">{t.ai.chatTitle}</h2>
      {turns.length > 0 && (
        <ol ref={listRef} className="mt-3 max-h-96 space-y-3 overflow-y-auto" aria-live="polite">
          {turns.map((turn, i) => (
            <li key={i} className={turn.role === "user" ? "flex justify-end" : "flex"}>
              <div
                className={`max-w-[85%] rounded-2xl px-3.5 py-2 text-sm ${
                  turn.role === "user" ? "bg-primary text-on-primary" : "bg-surface-2"
                }`}
              >
                <span className="sr-only">{turn.role === "user" ? t.ai.you : t.ai.assistant}: </span>
                {turn.role === "assistant" ? (
                  turn.content ? (
                    <Markdown text={turn.content} />
                  ) : (
                    <span className="animate-pulse text-subtle">{t.ai.thinking}</span>
                  )
                ) : (
                  turn.content
                )}
              </div>
            </li>
          ))}
        </ol>
      )}
      {error && (
        <p role="alert" className="mt-2 text-sm text-danger">
          {error}
        </p>
      )}
      <form onSubmit={send} className="mt-3 flex gap-2">
        <label htmlFor="ai-chat-input" className="sr-only">
          {t.ai.chatTitle}
        </label>
        <input
          id="ai-chat-input"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          maxLength={2000}
          placeholder={t.ai.chatPlaceholder}
          className="h-11 min-w-0 flex-1 rounded-full border border-border bg-surface px-4 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/25 transition-[border-color,box-shadow]"
        />
        <button
          type="submit"
          disabled={busy || !input.trim()}
          className="h-11 shrink-0 rounded-full bg-primary px-5 text-sm font-semibold text-on-primary disabled:opacity-50"
        >
          {t.ai.send}
        </button>
      </form>
    </div>
  );
}

export function AiEpisodePanel({
  episodeId,
  enabled,
  hasTranscript,
}: {
  episodeId: number;
  enabled: boolean;
  hasTranscript: boolean;
}) {
  if (!enabled) {
    return (
      <p className="flex items-center gap-2 rounded-xl border border-dashed border-border p-3 text-xs text-subtle">
        <SparkIcon size={14} />
        {t.ai.disabled}
      </p>
    );
  }
  return (
    <section
      aria-labelledby="ai-summary-title"
      className="grid gap-6 rounded-2xl border border-primary/25 bg-surface p-4 shadow-sm sm:p-5 lg:grid-cols-2"
    >
      <Summary episodeId={episodeId} hasTranscript={hasTranscript} />
      <Chat episodeId={episodeId} />
    </section>
  );
}
