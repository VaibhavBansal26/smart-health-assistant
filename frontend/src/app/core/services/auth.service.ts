import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, map, of, shareReplay, tap } from 'rxjs';
import { API_URL } from '../api.config';
import { AuthResponse, LoginPayload, RegisterPayload, User } from '../models/models';
import { AppointmentStore } from './appointment.store';
import { ChatStore } from './chat.store';
import { TokenStorage } from './token-storage.service';

/**
 * Owns the "who is logged in" state.
 * Components read `user()`, `isLoggedIn()` and `role()` as signals.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_URL);
  private readonly router = inject(Router);
  private readonly storage = inject(TokenStorage);
  private readonly appointments = inject(AppointmentStore);
  private readonly chat = inject(ChatStore);

  private readonly _user = signal<User | null>(null);

  readonly user = this._user.asReadonly();
  readonly isLoggedIn = computed(() => this._user() !== null);
  readonly role = computed(() => this._user()?.role ?? null);

  /** The in-flight refresh, shared so simultaneous callers cause ONE request (the cookie rotates on every use). */
  private refreshInFlight$?: Observable<boolean>;

  login(payload: LoginPayload): Observable<User> {
    return this.http
      .post<AuthResponse>(`${this.api}/auth/login`, payload)
      .pipe(tap((res) => this.startSession(res)), map((res) => res.user));
  }

  register(payload: RegisterPayload): Observable<User> {
    return this.http
      .post<AuthResponse>(`${this.api}/auth/register`, payload)
      .pipe(tap((res) => this.startSession(res)), map((res) => res.user));
  }

  logout(returnUrl?: string): void {
    // Best effort: revoke the refresh token on the server and clear its cookie. We sign out locally either way.
    this.http.post(`${this.api}/auth/logout`, {}).subscribe({ error: () => undefined });
    this.storage.clear();
    this._user.set(null);
    this.appointments.reset();
    this.chat.reset(); // never leave one user's conversation visible to the next
    this.router.navigate(['/login'], returnUrl ? { queryParams: { returnUrl } } : {});
  }

  /**
   * Trades the HttpOnly refresh cookie for a new access token (and user). Emits true on success, false if there is no
   * valid session. Never errors, so callers can just branch on the result. The interceptor adds the credentials and
   * CSRF header for this endpoint.
   */
  refresh(): Observable<boolean> {
    if (!this.refreshInFlight$) {
      this.refreshInFlight$ = this.http.post<AuthResponse>(`${this.api}/auth/refresh`, {}).pipe(
        tap((res) => this.startSession(res)),
        map(() => true),
        catchError((err) => {
          if (err?.status === 401) {
            this.storage.clear(); // the session is really gone; stop retrying on every page load
          }
          return of(false);
        }),
        finalize(() => (this.refreshInFlight$ = undefined)),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.refreshInFlight$;
  }

  /**
   * Runs once before the app renders (see provideAppInitializer in app.config). If this browser had a session, ask the
   * server for a fresh access token using the refresh cookie, so a page reload keeps the user signed in.
   */
  restoreSession(): Observable<unknown> {
    return this.storage.hasSessionHint() ? this.refresh() : of(false);
  }

  private startSession(res: AuthResponse): void {
    this.storage.set(res.token);
    this._user.set(res.user);
  }
}
