import { Injectable } from '@angular/core';

const HINT_KEY = 'health.signedIn';

/**
 * Holds the short-lived access token IN MEMORY ONLY.
 *
 * It is deliberately never written to localStorage/sessionStorage: anything stored there can be read by any script
 * on the page (XSS), so a stolen token would be a stolen login. Memory is wiped on refresh, which is fine because the
 * long-lived credential is an HttpOnly cookie that scripts cannot read; on startup the app exchanges it for a fresh
 * access token (see AuthService.restoreSession).
 *
 * The only thing persisted is a boolean HINT ("this browser had a session") so anonymous visitors do not fire a
 * pointless refresh request on every page load. It contains no credential.
 */
@Injectable({ providedIn: 'root' })
export class TokenStorage {
  private token: string | null = null;

  get(): string | null {
    return this.token;
  }

  set(token: string): void {
    this.token = token;
    this.writeHint(true);
  }

  clear(): void {
    this.token = null;
    this.writeHint(false);
  }

  /** True if this browser had a signed-in session (so a refresh attempt is worthwhile). */
  hasSessionHint(): boolean {
    try {
      return localStorage.getItem(HINT_KEY) === '1';
    } catch {
      return false;
    }
  }

  private writeHint(on: boolean): void {
    try {
      if (on) localStorage.setItem(HINT_KEY, '1');
      else localStorage.removeItem(HINT_KEY);
    } catch {
      /* storage unavailable (private mode): the app still works, it just refreshes on every load */
    }
  }
}
