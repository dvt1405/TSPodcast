"use client";

import { useSyncExternalStore } from "react";
import { player } from "./store";

export function usePlayerState() {
  return useSyncExternalStore(player.subscribeState, player.getState, player.getServerState);
}

export function usePlayerProgress() {
  return useSyncExternalStore(player.subscribeProgress, player.getProgress, player.getServerProgress);
}
