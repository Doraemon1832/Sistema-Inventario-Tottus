import { Injectable } from '@angular/core';

type DataScope = 'inventory' | 'operations' | 'supply' | 'all';

interface SyncMessage {
  scope: DataScope;
  timestamp: number;
}

@Injectable({ providedIn: 'root' })
export class DataSyncService {
  private readonly storageKey = 'tottus_data_sync';
  private readonly pollIntervalMs = 60_000;

  announce(scope: DataScope): void {
    try {
      localStorage.setItem(this.storageKey, JSON.stringify({ scope, timestamp: Date.now() } satisfies SyncMessage));
    } catch {
      // A successful server mutation must not be reported as failed if storage is unavailable.
    }
  }

  listen(scopes: DataScope[], refresh: () => void): () => void {
    let lastRefresh = 0;
    const refreshVisibleView = () => {
      if (document.visibilityState !== 'visible' || Date.now() - lastRefresh < 750) return;
      lastRefresh = Date.now();
      refresh();
    };
    const listener = (event: StorageEvent) => {
      if (event.key !== this.storageKey || !event.newValue) return;
      try {
        const message = JSON.parse(event.newValue) as SyncMessage;
        if (message.scope === 'all' || scopes.includes(message.scope)) refreshVisibleView();
      } catch {
        // Ignore malformed cross-tab messages and retain the last confirmed view.
      }
    };
    const onFocus = () => refreshVisibleView();
    const onVisibilityChange = () => refreshVisibleView();
    const timer = window.setInterval(refreshVisibleView, this.pollIntervalMs);
    window.addEventListener('storage', listener);
    window.addEventListener('focus', onFocus);
    document.addEventListener('visibilitychange', onVisibilityChange);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener('storage', listener);
      window.removeEventListener('focus', onFocus);
      document.removeEventListener('visibilitychange', onVisibilityChange);
    };
  }
}
