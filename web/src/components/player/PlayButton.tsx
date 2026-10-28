"use client";

import { useState } from "react";
import { CheckIcon, PauseIcon, PlayIcon, PlusIcon } from "@/components/icons";
import { t } from "@/lib/i18n";
import type { Track } from "@/lib/types";
import { usePlayerState } from "./hooks";
import { player } from "./store";

interface PlayButtonProps {
  track: Track;
  /** Replace the queue with this list when starting playback (e.g. a show's episodes). */
  queue?: Track[];
  variant?: "icon" | "pill";
  label?: string;
  className?: string;
}

export function PlayButton({ track, queue, variant = "icon", label, className = "" }: PlayButtonProps) {
  const state = usePlayerState();
  const isCurrent = state.queue[state.index]?.key === track.key;
  const isPlaying = isCurrent && state.playing;
  const busy = isCurrent && state.buffering;
  const text = isPlaying ? t.player.pause : (label ?? t.player.play);

  const onClick = () => {
    if (isCurrent) player.toggle();
    else player.play(track, queue);
  };

  if (variant === "pill") {
    return (
      <button
        type="button"
        onClick={onClick}
        aria-label={`${text}: ${track.title}`}
        className={`inline-flex items-center gap-2 rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-on-primary shadow-sm transition hover:opacity-90 disabled:opacity-60 ${className}`}
      >
        {isPlaying ? <PauseIcon size={18} /> : <PlayIcon size={18} />}
        <span>{busy ? t.player.loading : text}</span>
      </button>
    );
  }

  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={`${isPlaying ? t.player.pause : t.player.play}: ${track.title}`}
      aria-pressed={isPlaying}
      className={`inline-flex size-10 shrink-0 items-center justify-center rounded-full bg-primary text-on-primary shadow-sm transition hover:opacity-90 ${busy ? "animate-pulse" : ""} ${className}`}
    >
      {isPlaying ? <PauseIcon size={18} /> : <PlayIcon size={18} />}
    </button>
  );
}

export function QueueButton({ track, className = "" }: { track: Track; className?: string }) {
  const state = usePlayerState();
  const [justAdded, setJustAdded] = useState(false);
  const inQueue = state.queue.some((q) => q.key === track.key);
  return (
    <button
      type="button"
      onClick={() => {
        player.enqueue(track);
        setJustAdded(true);
        window.setTimeout(() => setJustAdded(false), 1500);
      }}
      disabled={inQueue}
      aria-label={`${inQueue ? t.player.added : t.player.addToQueue}: ${track.title}`}
      title={inQueue ? t.player.added : t.player.addToQueue}
      className={`inline-flex size-9 shrink-0 items-center justify-center rounded-full border border-border text-muted transition hover:border-primary hover:text-primary disabled:cursor-default disabled:opacity-70 ${className}`}
    >
      {inQueue || justAdded ? <CheckIcon size={16} /> : <PlusIcon size={16} />}
    </button>
  );
}
