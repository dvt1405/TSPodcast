// Client-only audio player store.
//
// One module-level instance owns a detached HTMLAudioElement. The root layout
// never unmounts on client navigation and this module is never re-evaluated,
// so playback survives route changes. Components read it through
// useSyncExternalStore (see hooks.ts); state and progress are separate
// snapshots so the 4 Hz timeupdate only re-renders the progress bar.

import type { Track } from "@/lib/types";

export interface PlayerState {
  queue: Track[];
  index: number;
  playing: boolean;
  buffering: boolean;
  rate: number;
  error: boolean;
}

export interface Progress {
  time: number;
  duration: number;
}

export const RATES = [0.75, 1, 1.25, 1.5, 1.75, 2] as const;

const STATE_KEY = "tsp.player.v1";
const POSITIONS_KEY = "tsp.positions.v1";
const MAX_QUEUE = 100;
const MAX_POSITIONS = 300;
const SAVE_EVERY_MS = 5000;

const EMPTY_STATE: PlayerState = {
  queue: [],
  index: -1,
  playing: false,
  buffering: false,
  rate: 1,
  error: false,
};
const EMPTY_PROGRESS: Progress = { time: 0, duration: 0 };

type SavedPositions = Record<string, { t: number; d: number; u: number }>;

function readJson<T>(key: string): T | null {
  try {
    const raw = window.localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}

function writeJson(key: string, value: unknown): void {
  try {
    window.localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Quota exceeded or storage disabled: playback still works, resume does not.
  }
}

function isHls(src: string): boolean {
  return /\.m3u8(\?|$)/i.test(src);
}

function isTrack(value: unknown): value is Track {
  const t = value as Track;
  return Boolean(t && typeof t.key === "string" && typeof t.src === "string" && typeof t.title === "string");
}

class PlayerStore {
  private state: PlayerState = EMPTY_STATE;
  private progress: Progress = EMPTY_PROGRESS;
  private stateListeners = new Set<() => void>();
  private progressListeners = new Set<() => void>();
  private audio: HTMLAudioElement | null = null;
  private hls: { destroy(): void } | null = null;
  private loadedKey: string | null = null;
  private pendingSeek: number | null = null;
  private requestedStart: { key: string; time: number } | null = null;
  private lastSave = 0;
  private lastPositionState = 0;
  private initialized = false;

  // ---------- useSyncExternalStore plumbing ----------

  subscribeState = (listener: () => void) => {
    this.init();
    this.stateListeners.add(listener);
    return () => this.stateListeners.delete(listener);
  };
  subscribeProgress = (listener: () => void) => {
    this.init();
    this.progressListeners.add(listener);
    return () => this.progressListeners.delete(listener);
  };
  getState = () => this.state;
  getProgress = () => this.progress;
  getServerState = () => EMPTY_STATE;
  getServerProgress = () => EMPTY_PROGRESS;

  private setState(patch: Partial<PlayerState>): void {
    const next = { ...this.state, ...patch };
    const persist = patch.queue !== undefined || patch.index !== undefined || patch.rate !== undefined;
    this.state = next;
    this.stateListeners.forEach((l) => l());
    if (persist) {
      writeJson(STATE_KEY, { queue: next.queue.slice(0, MAX_QUEUE), index: next.index, rate: next.rate });
    }
  }

  private setProgress(patch: Partial<Progress>): void {
    this.progress = { ...this.progress, ...patch };
    this.progressListeners.forEach((l) => l());
  }

  // ---------- setup ----------

  private init(): void {
    if (this.initialized || typeof window === "undefined") return;
    this.initialized = true;

    const audio = new Audio();
    audio.preload = "metadata";
    this.audio = audio;

    audio.addEventListener("playing", () => this.setState({ playing: true, buffering: false, error: false }));
    audio.addEventListener("play", () => {
      this.setState({ playing: true });
      this.setPlaybackState("playing");
    });
    audio.addEventListener("pause", () => {
      this.setState({ playing: false, buffering: false });
      this.setPlaybackState("paused");
      this.savePosition(true);
    });
    audio.addEventListener("waiting", () => this.setState({ buffering: true }));
    audio.addEventListener("canplay", () => this.setState({ buffering: false }));
    audio.addEventListener("loadedmetadata", () => this.onMetadata());
    audio.addEventListener("durationchange", () =>
      this.setProgress({ duration: Number.isFinite(audio.duration) ? audio.duration : 0 }),
    );
    audio.addEventListener("timeupdate", () => this.onTimeUpdate());
    audio.addEventListener("ended", () => this.onEnded());
    audio.addEventListener("error", () => {
      if (this.hls) return; // hls.js reports its own fatal errors
      if (!audio.getAttribute("src")) return;
      this.setState({ error: true, playing: false, buffering: false });
    });

    window.addEventListener("pagehide", () => this.savePosition(true));
    this.setupMediaSession();
    this.restore();
  }

  private restore(): void {
    const saved = readJson<{ queue?: unknown[]; index?: number; rate?: number }>(STATE_KEY);
    if (!saved) return;
    const queue = (saved.queue ?? []).filter(isTrack).slice(0, MAX_QUEUE);
    const index = Math.min(Math.max(Number(saved.index ?? 0), 0), queue.length - 1);
    const rate = RATES.includes(saved.rate as (typeof RATES)[number]) ? (saved.rate as number) : 1;
    this.state = { ...this.state, queue, index: queue.length ? index : -1, rate };
    const track = queue[index];
    if (track) {
      // Don't download anything until the user presses play; just show where
      // they left off.
      const pos = track.live ? null : this.getSavedPosition(track.key);
      this.progress = { time: pos ?? 0, duration: track.duration ?? 0 };
      this.updateMetadata(track);
    }
  }

  // ---------- positions (resume) ----------

  getSavedPosition(key: string): number | null {
    if (typeof window === "undefined") return null;
    const entry = readJson<SavedPositions>(POSITIONS_KEY)?.[key];
    if (!entry || entry.t < 5) return null;
    if (entry.d > 0 && entry.d - entry.t < 15) return null;
    return entry.t;
  }

  private savePosition(force = false): void {
    const audio = this.audio;
    const track = this.current();
    if (!audio || !track || track.live || this.loadedKey !== track.key) return;
    const now = Date.now();
    if (!force && now - this.lastSave < SAVE_EVERY_MS) return;
    this.lastSave = now;
    const t = audio.currentTime;
    const d = Number.isFinite(audio.duration) ? audio.duration : 0;
    const positions = readJson<SavedPositions>(POSITIONS_KEY) ?? {};
    if (t < 5 && !positions[track.key]) return;
    if (d > 0 && d - t < 15) delete positions[track.key];
    else positions[track.key] = { t: Math.floor(t), d: Math.floor(d), u: now };
    const keys = Object.keys(positions);
    if (keys.length > MAX_POSITIONS) {
      keys
        .sort((a, b) => positions[a].u - positions[b].u)
        .slice(0, keys.length - MAX_POSITIONS)
        .forEach((k) => delete positions[k]);
    }
    writeJson(POSITIONS_KEY, positions);
  }

  // ---------- loading ----------

  current(): Track | null {
    return this.state.queue[this.state.index] ?? null;
  }

  private async load(track: Track, autoplay: boolean): Promise<void> {
    const audio = this.audio;
    if (!audio) return;
    if (this.loadedKey && this.loadedKey !== track.key) this.savePosition(true);

    this.hls?.destroy();
    this.hls = null;
    this.loadedKey = track.key;
    const requested = this.requestedStart?.key === track.key ? this.requestedStart.time : null;
    this.requestedStart = null;
    this.pendingSeek = track.live ? null : (requested ?? this.getSavedPosition(track.key));
    this.setProgress({ time: this.pendingSeek ?? 0, duration: track.duration ?? 0 });
    this.setState({ error: false, buffering: autoplay });
    this.updateMetadata(track);

    const rate = track.live ? 1 : this.state.rate;
    audio.defaultPlaybackRate = rate;

    if (isHls(track.src) && !audio.canPlayType("application/vnd.apple.mpegurl")) {
      audio.removeAttribute("src");
      const { default: Hls } = await import("hls.js");
      if (this.loadedKey !== track.key) return; // superseded while loading
      if (Hls.isSupported()) {
        const hls = new Hls({ enableWorker: true, lowLatencyMode: false });
        hls.on(Hls.Events.ERROR, (_event, data) => {
          if (data.fatal) this.setState({ error: true, playing: false, buffering: false });
        });
        hls.loadSource(track.src);
        hls.attachMedia(audio);
        this.hls = hls;
      } else {
        audio.src = track.src;
      }
    } else {
      audio.src = track.src;
    }
    audio.playbackRate = rate;
    if (autoplay) this.playAudio();
  }

  private playAudio(): void {
    const audio = this.audio;
    if (!audio) return;
    audio.play().catch((error: unknown) => {
      const name = error instanceof DOMException ? error.name : "";
      if (name === "AbortError") return; // superseded by another load
      if (name === "NotAllowedError") {
        this.setState({ playing: false, buffering: false });
        return;
      }
      this.setState({ error: true, playing: false, buffering: false });
    });
  }

  private onMetadata(): void {
    const audio = this.audio;
    if (!audio) return;
    const duration = Number.isFinite(audio.duration) ? audio.duration : 0;
    this.setProgress({ duration });
    if (this.pendingSeek != null && duration > 0 && this.pendingSeek < duration - 15) {
      audio.currentTime = this.pendingSeek;
    }
    this.pendingSeek = null;
    audio.playbackRate = this.current()?.live ? 1 : this.state.rate;
  }

  private onTimeUpdate(): void {
    const audio = this.audio;
    if (!audio || this.loadedKey !== this.current()?.key) return;
    this.setProgress({ time: audio.currentTime });
    if (!audio.paused) this.savePosition();
    const now = Date.now();
    if (now - this.lastPositionState > 1000) {
      this.lastPositionState = now;
      this.updatePositionState();
    }
  }

  private onEnded(): void {
    const track = this.current();
    if (track && !track.live) {
      const positions = readJson<SavedPositions>(POSITIONS_KEY) ?? {};
      delete positions[track.key];
      writeJson(POSITIONS_KEY, positions);
    }
    if (this.state.index < this.state.queue.length - 1) {
      this.jumpTo(this.state.index + 1);
    } else {
      this.setState({ playing: false });
    }
  }

  // ---------- public actions ----------

  /** Plays `track`. With `queue`, replaces the queue (e.g. "play this show"). */
  play = (track: Track, queue?: Track[]): void => {
    this.init();
    if (this.current()?.key === track.key && this.loadedKey === track.key) {
      if (this.audio?.paused) this.playAudio();
      return;
    }
    if (queue && queue.length > 0) {
      const list = queue.slice(0, MAX_QUEUE);
      let index = list.findIndex((t) => t.key === track.key);
      if (index < 0) {
        list.unshift(track);
        index = 0;
      }
      this.setState({ queue: list, index });
    } else {
      const existing = this.state.queue.findIndex((t) => t.key === track.key);
      if (existing >= 0) {
        this.setState({ index: existing });
      } else {
        const queue2 = [...this.state.queue];
        const at = this.state.index >= 0 ? this.state.index + 1 : 0;
        queue2.splice(at, 0, track);
        this.setState({ queue: queue2.slice(0, MAX_QUEUE), index: at });
      }
    }
    void this.load(track, true);
  };

  toggle = (): void => {
    const track = this.current();
    const audio = this.audio;
    if (!track || !audio) return;
    if (this.loadedKey !== track.key) {
      void this.load(track, true);
      return;
    }
    if (audio.paused) this.playAudio();
    else audio.pause();
  };

  pause = (): void => {
    this.audio?.pause();
  };

  enqueue = (track: Track): void => {
    this.init();
    if (this.state.queue.some((t) => t.key === track.key)) return;
    const queue = [...this.state.queue, track].slice(0, MAX_QUEUE);
    this.setState({ queue, index: this.state.index < 0 ? 0 : this.state.index });
  };

  playNext = (track: Track): void => {
    this.init();
    const queue = this.state.queue.filter((t) => t.key !== track.key);
    const currentKey = this.current()?.key;
    const currentIndex = queue.findIndex((t) => t.key === currentKey);
    queue.splice(currentIndex + 1, 0, track);
    this.setState({
      queue: queue.slice(0, MAX_QUEUE),
      index: currentIndex >= 0 ? currentIndex : 0,
    });
  };

  jumpTo = (index: number): void => {
    const track = this.state.queue[index];
    if (!track) return;
    this.setState({ index });
    void this.load(track, true);
  };

  removeAt = (index: number): void => {
    const { queue, index: current } = this.state;
    if (index < 0 || index >= queue.length) return;
    const next = queue.filter((_, i) => i !== index);
    if (index === current) {
      this.audio?.pause();
      this.hls?.destroy();
      this.hls = null;
      this.audio?.removeAttribute("src");
      this.loadedKey = null;
      const newIndex = Math.min(index, next.length - 1);
      this.setState({ queue: next, index: newIndex, playing: false });
      this.setProgress({ time: 0, duration: next[newIndex]?.duration ?? 0 });
      if (next[newIndex]) this.updateMetadata(next[newIndex]);
      else this.clearMetadata();
    } else {
      this.setState({ queue: next, index: index < current ? current - 1 : current });
    }
  };

  clear = (): void => {
    this.savePosition(true);
    this.audio?.pause();
    this.hls?.destroy();
    this.hls = null;
    this.audio?.removeAttribute("src");
    this.loadedKey = null;
    this.setState({ queue: [], index: -1, playing: false, buffering: false, error: false });
    this.setProgress(EMPTY_PROGRESS);
    this.clearMetadata();
  };

  next = (): void => {
    if (this.state.index < this.state.queue.length - 1) this.jumpTo(this.state.index + 1);
  };

  previous = (): void => {
    if ((this.audio?.currentTime ?? 0) > 5 || this.state.index <= 0) this.seek(0);
    else this.jumpTo(this.state.index - 1);
  };

  seek = (time: number): void => {
    const audio = this.audio;
    const track = this.current();
    if (!audio || !track || track.live) return;
    if (this.loadedKey !== track.key) {
      // Not loaded yet (restored session): remember and start from there.
      this.setProgress({ time });
      this.requestedStart = { key: track.key, time };
      return;
    }
    const duration = Number.isFinite(audio.duration) ? audio.duration : this.progress.duration;
    audio.currentTime = Math.max(0, duration ? Math.min(time, duration - 0.5) : time);
    this.setProgress({ time: audio.currentTime });
    this.updatePositionState();
  };

  skipBy = (delta: number): void => {
    this.seek((this.audio?.currentTime ?? this.progress.time) + delta);
  };

  setRate = (rate: number): void => {
    this.setState({ rate });
    if (this.audio && !this.current()?.live) {
      this.audio.playbackRate = rate;
      this.audio.defaultPlaybackRate = rate;
    }
  };

  cycleRate = (): void => {
    const i = RATES.indexOf(this.state.rate as (typeof RATES)[number]);
    this.setRate(RATES[(i + 1) % RATES.length]);
  };

  // ---------- Media Session (lock screen / notification controls) ----------

  private setupMediaSession(): void {
    if (!("mediaSession" in navigator)) return;
    const ms = navigator.mediaSession;
    const handlers: [MediaSessionAction, MediaSessionActionHandler][] = [
      ["play", () => this.toggle()],
      ["pause", () => this.pause()],
      ["seekbackward", (d) => this.skipBy(-(d.seekOffset ?? 15))],
      ["seekforward", (d) => this.skipBy(d.seekOffset ?? 30)],
      ["previoustrack", () => this.previous()],
      ["nexttrack", () => this.next()],
      ["seekto", (d) => d.seekTime != null && this.seek(d.seekTime)],
      ["stop", () => this.pause()],
    ];
    for (const [action, handler] of handlers) {
      try {
        ms.setActionHandler(action, handler);
      } catch {
        // Action not supported by this browser.
      }
    }
  }

  private updateMetadata(track: Track): void {
    if (typeof navigator === "undefined" || !("mediaSession" in navigator)) return;
    try {
      navigator.mediaSession.metadata = new MediaMetadata({
        title: track.title,
        artist: track.subtitle,
        album: "TSPodcast",
        artwork: track.image
          ? [
              { src: track.image, sizes: "512x512" },
              { src: track.image, sizes: "1400x1400" },
            ]
          : [],
      });
    } catch {
      // MediaMetadata unsupported.
    }
  }

  private clearMetadata(): void {
    if (typeof navigator === "undefined" || !("mediaSession" in navigator)) return;
    navigator.mediaSession.metadata = null;
    this.setPlaybackState("none");
  }

  private setPlaybackState(state: MediaSessionPlaybackState): void {
    if (typeof navigator !== "undefined" && "mediaSession" in navigator) {
      navigator.mediaSession.playbackState = state;
    }
  }

  private updatePositionState(): void {
    const audio = this.audio;
    if (!audio || !("mediaSession" in navigator) || !navigator.mediaSession.setPositionState) return;
    const duration = audio.duration;
    if (!Number.isFinite(duration) || duration <= 0) return;
    try {
      navigator.mediaSession.setPositionState({
        duration,
        playbackRate: audio.playbackRate || 1,
        position: Math.min(audio.currentTime, duration),
      });
    } catch {
      // Invalid state during a source change.
    }
  }
}

export const player = new PlayerStore();
