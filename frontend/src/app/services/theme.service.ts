import { DOCUMENT } from '@angular/common';
import { Inject, Injectable, signal } from '@angular/core';

export type AppTheme = 'light' | 'dark';
const STORAGE_KEY = 'tottus-theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<AppTheme>('light');
  private readonly preference = typeof window === 'undefined' ? null : window.matchMedia('(prefers-color-scheme: dark)');

  constructor(@Inject(DOCUMENT) private document: Document) {
    let stored: string | null = null;
    try { stored = window.localStorage.getItem(STORAGE_KEY); } catch { /* storage may be unavailable */ }
    const initial = stored === 'light' || stored === 'dark' ? stored : this.preference?.matches ? 'dark' : 'light';
    this.apply(initial);
    this.preference?.addEventListener('change', event => {
      try {
        if (!window.localStorage.getItem(STORAGE_KEY)) this.apply(event.matches ? 'dark' : 'light');
      } catch { this.apply(event.matches ? 'dark' : 'light'); }
    });
  }

  toggle(): void { this.set(this.theme() === 'dark' ? 'light' : 'dark'); }

  set(theme: AppTheme): void {
    try { window.localStorage.setItem(STORAGE_KEY, theme); } catch { /* apply for this session */ }
    this.apply(theme);
  }

  private apply(theme: AppTheme): void {
    this.theme.set(theme);
    this.document.documentElement.dataset['theme'] = theme;
    this.document.documentElement.style.colorScheme = theme;
  }
}
