import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, map, of, tap } from 'rxjs';
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
    this.storage.clear();
    this._user.set(null);
    this.appointments.reset();
    this.chat.reset(); // never leave one user's conversation visible to the next
    this.router.navigate(['/login'], returnUrl ? { queryParams: { returnUrl } } : {});
  }

  /**
   * Runs once before the app renders (see provideAppInitializer in app.config).
   * If a token is saved, ask the server who it belongs to, so a page refresh
   * keeps the user logged in. Never errors: a bad token just means "logged out".
   */
  restoreSession(): Observable<unknown> {
    if (!this.storage.get()) {
      return of(null);
    }
    return this.http.get<User>(`${this.api}/auth/me`).pipe(
      tap((user) => this._user.set(user)),
      catchError(() => {
        this.storage.clear();
        return of(null);
      }),
    );
  }

  private startSession(res: AuthResponse): void {
    this.storage.set(res.token);
    this._user.set(res.user);
  }
}
