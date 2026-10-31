"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import {
  Back15Icon,
  CloseIcon,
  Forward30Icon,
  NextIcon,
  PauseIcon,
  PlayIcon,
  PrevIcon,
  QueueIcon,
} from "@/components/icons";
import { Artwork } from "@/components/Artwork";
import { formatClock } from "@/lib/format";
import { t } from "@/lib/i18n";
import { usePlayerProgress, usePlayerState } from "./hooks";
import { player } from "./store";

function SeekBar({ live }: { live: boolean }) {
  const { time, duration } = usePlayerProgress();
  const [dragValue, setDragValue] = useState<number | null>(null);

  if (live) {
    return (
      <div className="flex items-center gap-2 pt-2 text-xs">
        <span className="inline-flex items-center gap-1.5 rounded-full bg-accent/15 px-2 py-0.5 font-bold text-accent">
          <span className="size-1.5 animate-pulse rounded-full bg-accent" aria-hidden="true" />
          {t.player.live}
        </span>
      </div>
    );
  }

  const value = dragValue ?? time;
  const max = duration > 0 ? duration : Math.max(value, 1);
  const pct = max > 0 ? Math.min(100, (value / max) * 100) : 0;
  return (
    <div className="flex items-center gap-2 pt-1 text-[11px] tabular-nums text-subtle">
      <span className="w-12 text-right">{formatClock(value)}</span>
      <input
        type="range"
        className="seek flex-1"
        min={0}
        max={Math.floor(max)}
        step={1}
        value={Math.floor(value)}
        aria-label={t.player.seek}
        aria-valuetext={`${formatClock(value)} / ${duration > 0 ? formatClock(duration) : "--:--"}`}
        style={{ ["--progress" as string]: `${pct}%` }}
        onChange={(e) => setDragValue(Number(e.target.value))}
        onPointerUp={(e) => {
          player.seek(Number((e.target as HTMLInputElement).value));
          setDragValue(null);
        }}
        onKeyUp={(e) => {
          player.seek(Number((e.target as HTMLInputElement).value));
          setDragValue(null);
        }}
        onBlur={() => setDragValue(null)}
      />
      <span className="w-12">{duration > 0 ? formatClock(duration) : "--:--"}</span>
    </div>
  );
}

function QueuePanel({ onClose }: { onClose: () => void }) {
  const state = usePlayerState();

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div
      id="player-queue"
      role="region"
      aria-label={t.player.queue}
      className="queue-panel mx-auto max-h-[50vh] max-w-6xl overflow-y-auto overscroll-contain border-b border-border px-3 py-3"
    >
      <div className="mb-2 flex items-center justify-between">
        <h2 className="text-sm font-bold">
          {t.player.queue} <span className="font-normal text-subtle">({state.queue.length})</span>
        </h2>
        <div className="flex items-center gap-2">
          {state.queue.length > 0 && (
            <button
              type="button"
              onClick={() => player.clear()}
              className="rounded-full px-3 py-1 text-xs font-semibold text-danger hover:bg-surface-2"
            >
              {t.player.clear}
            </button>
          )}
          <button
            type="button"
            onClick={onClose}
            aria-label={t.player.close}
            className="inline-flex size-8 items-center justify-center rounded-full hover:bg-surface-2"
          >
            <CloseIcon size={16} />
          </button>
        </div>
      </div>
      {state.queue.length === 0 ? (
        <p className="text-sm text-subtle">{t.player.queueEmpty}</p>
      ) : (
        <ol className="space-y-1">
          {state.queue.map((track, i) => {
            const current = i === state.index;
            return (
              <li
                key={track.key}
                className={`flex items-center gap-3 rounded-lg p-1.5 ${current ? "bg-primary-soft" : "hover:bg-surface-2"}`}
              >
                <button
                  type="button"
                  onClick={() => player.jumpTo(i)}
                  className="flex min-w-0 flex-1 items-center gap-3 text-left"
                  aria-current={current ? "true" : undefined}
                >
                  <Artwork src={track.image} alt="" size={40} className="rounded-md" />
                  <span className="min-w-0">
                    <span className={`block truncate text-sm ${current ? "font-bold text-primary" : "font-medium"}`}>
                      {track.title}
                    </span>
                    <span className="block truncate text-xs text-subtle">{track.subtitle}</span>
                  </span>
                </button>
                <button
                  type="button"
                  onClick={() => player.removeAt(i)}
                  aria-label={`${t.player.remove}: ${track.title}`}
                  className="inline-flex size-8 shrink-0 items-center justify-center rounded-full text-subtle hover:bg-surface-2 hover:text-danger"
                >
                  <CloseIcon size={14} />
                </button>
              </li>
            );
          })}
        </ol>
      )}
    </div>
  );
}

const ctrl =
  "press inline-flex size-10 shrink-0 items-center justify-center rounded-full text-foreground hover:bg-surface-2 disabled:opacity-40";

export function PlayerBar() {
  const state = usePlayerState();
  const [queueOpen, setQueueOpen] = useState(false);
  const track = state.queue[state.index];
  if (!track) return null;
  const live = Boolean(track.live);

  return (
    <>
      {/* Spacer so the fixed bar never covers the footer. */}
      <div aria-hidden="true" className="h-32" />
      <section
        aria-label={t.player.player}
        className="player-bar fixed inset-x-0 bottom-0 z-40 border-t border-border bg-surface/95 pb-[env(safe-area-inset-bottom)] shadow-[0_-4px_24px_rgb(0_0_0/0.08)] backdrop-blur"
      >
        {queueOpen && <QueuePanel onClose={() => setQueueOpen(false)} />}
        <div className="mx-auto max-w-6xl px-3 pb-2">
          <SeekBar live={live} />
          <div className="flex items-center gap-2 sm:gap-3">
            {/* Keyed by track so the artwork and title fade in when the track changes. */}
            <div key={track.key} className="track-swap flex min-w-0 flex-1 items-center gap-2 sm:gap-3">
              <Artwork src={track.image} alt="" size={48} className="rounded-lg" />
              <div className="min-w-0 flex-1">
                {track.href ? (
                  <Link href={track.href} className="block truncate text-sm font-semibold hover:underline">
                    {track.title}
                  </Link>
                ) : (
                  <p className="truncate text-sm font-semibold">{track.title}</p>
                )}
                <p className="truncate text-xs text-subtle" aria-live="polite">
                  {state.error ? <span className="text-danger">{t.player.error}</span> : track.subtitle}
                </p>
              </div>
            </div>

            <div className="flex items-center gap-0.5 sm:gap-1">
              <button
                type="button"
                className={`${ctrl} hidden sm:inline-flex`}
                onClick={() => player.previous()}
                aria-label={t.player.previous}
              >
                <PrevIcon size={18} />
              </button>
              {!live && (
                <button type="button" className={ctrl} onClick={() => player.skipBy(-15)} aria-label={t.player.back15}>
                  <Back15Icon size={22} />
                </button>
              )}
              <button
                type="button"
                onClick={() => player.toggle()}
                aria-label={state.playing ? t.player.pause : t.player.play}
                className={`press inline-flex size-11 shrink-0 items-center justify-center rounded-full bg-primary text-on-primary shadow hover:opacity-90 ${state.buffering ? "animate-pulse" : ""}`}
              >
                {state.playing ? <PauseIcon size={20} /> : <PlayIcon size={20} />}
              </button>
              {!live && (
                <button type="button" className={ctrl} onClick={() => player.skipBy(30)} aria-label={t.player.forward30}>
                  <Forward30Icon size={22} />
                </button>
              )}
              <button
                type="button"
                className={`${ctrl} hidden sm:inline-flex`}
                onClick={() => player.next()}
                disabled={state.index >= state.queue.length - 1}
                aria-label={t.player.next}
              >
                <NextIcon size={18} />
              </button>
              {!live && (
                <button
                  type="button"
                  onClick={() => player.cycleRate()}
                  aria-label={`${t.player.speed}: ${state.rate}x`}
                  className="inline-flex h-8 min-w-11 shrink-0 items-center justify-center rounded-full border border-border px-2 text-xs font-bold tabular-nums hover:border-primary hover:text-primary"
                >
                  {state.rate}x
                </button>
              )}
              <button
                type="button"
                className={`${ctrl} ${queueOpen ? "text-primary" : ""}`}
                onClick={() => setQueueOpen((v) => !v)}
                aria-label={t.player.queue}
                aria-expanded={queueOpen}
                aria-controls="player-queue"
              >
                <QueueIcon size={20} />
              </button>
            </div>
          </div>
        </div>
      </section>
    </>
  );
}
