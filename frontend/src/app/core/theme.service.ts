import { DOCUMENT } from '@angular/common';
import { Injectable, effect, inject, signal } from '@angular/core';

const KEY = 'theme';

/** Modo claro/oscuro: respeta la preferencia guardada, o la del sistema si no hay ninguna. */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private doc = inject(DOCUMENT);
  dark = signal(this.initial());

  constructor() {
    effect(() => {
      const dark = this.dark();
      this.doc.documentElement.classList.toggle('dark-theme', dark);
      try { localStorage.setItem(KEY, dark ? 'dark' : 'light'); } catch { /* almacenamiento no disponible */ }
    });
  }

  toggle() { this.dark.update(d => !d); }

  private initial(): boolean {
    try {
      const saved = localStorage.getItem(KEY);
      if (saved) return saved === 'dark';
    } catch { /* ignorar */ }
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
  }
}
