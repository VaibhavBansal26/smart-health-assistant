import { Injectable } from '@angular/core';

const KEY = 'health.token';

/**
 * Thin wrapper over localStorage. Storage can throw (private mode, blocked
 * cookies), so every access is guarded and the app still works without it.
 *
 * Note: a JWT in localStorage is readable by any script on the page (XSS).
 * It is the simplest option for learning; production apps often prefer an
 * httpOnly cookie instead.
 */
@Injectable({ providedIn: 'root' })
export class TokenStorage {
  get(): string | null {
    try {
      return localStorage.getItem(KEY);
    } catch {
      return null;
    }
  }

  set(token: string): void {
    try {
      localStorage.setItem(KEY, token);
    } catch {
      /* ignore: user just has to log in again after refresh */
    }
  }

  clear(): void {
    try {
      localStorage.removeItem(KEY);
    } catch {
      /* ignore */
    }
  }
}
