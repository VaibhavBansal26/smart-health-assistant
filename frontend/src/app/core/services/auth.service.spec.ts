import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { API_URL } from '../api.config';
import { User } from '../models/models';
import { AuthService } from './auth.service';
import { TokenStorage } from './token-storage.service';

const API = 'http://api.test';
const user: User = {
  id: 1, fullName: 'Maya Patel', email: 'maya@test.dev', role: 'PATIENT', specialty: null, emailVerified: false, active: true,
};
const session = (token = 'access-1') => ({ token, expiresInSeconds: 900, user });

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;
  let storage: TokenStorage;
  let navigate: jasmine.Spy;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: API_URL, useValue: API }],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    storage = TestBed.inject(TokenStorage);
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
  });
  afterEach(() => http.verify());

  it('login keeps the access token in memory and exposes the user', () => {
    let result: User | undefined;
    auth.login({ email: user.email, password: 'x' }).subscribe((u) => (result = u));
    http.expectOne(`${API}/auth/login`).flush(session('access-xyz'));

    expect(result).toEqual(user);
    expect(auth.user()).toEqual(user);
    expect(auth.isLoggedIn()).toBeTrue();
    expect(auth.role()).toBe('PATIENT');
    expect(storage.get()).toBe('access-xyz');
  });

  describe('restoreSession (page load)', () => {
    it('does nothing when this browser never had a session: no pointless 401 for anonymous visitors', () => {
      let emitted: unknown;
      auth.restoreSession().subscribe((v) => (emitted = v));
      http.expectNone(`${API}/auth/refresh`);
      expect(emitted).toBeFalse();
      expect(auth.isLoggedIn()).toBeFalse();
    });

    it('uses the refresh cookie to sign the user back in after a reload', () => {
      storage.set('old'); // leaves the hint behind
      TestBed.resetTestingModule(); // simulate the reload: memory gone, hint remains
      TestBed.configureTestingModule({
        providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: API_URL, useValue: API }],
      });
      const reloaded = TestBed.inject(AuthService);
      const reloadedHttp = TestBed.inject(HttpTestingController);
      expect(TestBed.inject(TokenStorage).get()).toBeNull();

      let ok: unknown;
      reloaded.restoreSession().subscribe((v) => (ok = v));
      reloadedHttp.expectOne(`${API}/auth/refresh`).flush(session('fresh'));

      expect(ok).toBeTrue();
      expect(reloaded.user()).toEqual(user);
      expect(TestBed.inject(TokenStorage).get()).toBe('fresh');
      reloadedHttp.verify();
    });
  });

  describe('refresh', () => {
    it('a rejected session (401) leaves the user signed out and forgets the hint', () => {
      storage.set('t');
      let ok: unknown;
      auth.refresh().subscribe((v) => (ok = v));
      http.expectOne(`${API}/auth/refresh`).flush({ message: 'expired' }, { status: 401, statusText: 'Unauthorized' });

      expect(ok).toBeFalse();
      expect(storage.get()).toBeNull();
      expect(storage.hasSessionHint()).toBeFalse();
    });

    it('a temporary network failure does NOT throw the session away', () => {
      storage.set('t');
      let ok: unknown;
      auth.refresh().subscribe((v) => (ok = v));
      http.expectOne(`${API}/auth/refresh`).error(new ProgressEvent('error'), { status: 0 });

      expect(ok).toBeFalse();
      expect(storage.hasSessionHint()).toBeTrue(); // we can try again later
    });

    it('simultaneous callers share one request (the cookie rotates, so two would race)', () => {
      const results: unknown[] = [];
      auth.refresh().subscribe((v) => results.push(v));
      auth.refresh().subscribe((v) => results.push(v));
      auth.refresh().subscribe((v) => results.push(v));

      http.expectOne(`${API}/auth/refresh`).flush(session('shared'));
      expect(results).toEqual([true, true, true]);
    });

    it('a later refresh makes a new request', () => {
      auth.refresh().subscribe();
      http.expectOne(`${API}/auth/refresh`).flush(session('one'));
      auth.refresh().subscribe();
      http.expectOne(`${API}/auth/refresh`).flush(session('two'));
      expect(storage.get()).toBe('two');
    });
  });

  it('logout revokes the session server-side, clears everything locally and goes to /login', () => {
    auth.login({ email: user.email, password: 'x' }).subscribe();
    http.expectOne(`${API}/auth/login`).flush(session());

    auth.logout('/appointments');

    http.expectOne(`${API}/auth/logout`).flush(null, { status: 204, statusText: 'No Content' });
    expect(auth.user()).toBeNull();
    expect(storage.get()).toBeNull();
    expect(storage.hasSessionHint()).toBeFalse();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/appointments' } });
  });

  it('logout still signs out locally when the server cannot be reached', () => {
    auth.login({ email: user.email, password: 'x' }).subscribe();
    http.expectOne(`${API}/auth/login`).flush(session());
    auth.logout();
    http.expectOne(`${API}/auth/logout`).error(new ProgressEvent('error'), { status: 0 });
    expect(auth.isLoggedIn()).toBeFalse();
  });

  describe('account emails', () => {
    function signIn(): void {
      auth.login({ email: user.email, password: 'x' }).subscribe();
      http.expectOne(`${API}/auth/login`).flush(session());
    }

    it('verifyEmail redeems the token and, when signed in, refreshes the profile so the banner disappears', () => {
      signIn();
      expect(auth.user()?.emailVerified).toBeFalse();

      let finished = false;
      auth.verifyEmail('tok-123').subscribe(() => (finished = true));
      const verify = http.expectOne(`${API}/auth/verify-email`);
      expect(verify.request.body).toEqual({ token: 'tok-123' });
      verify.flush(null, { status: 204, statusText: 'No Content' });
      http.expectOne(`${API}/auth/refresh`).flush({ ...session('after'), user: { ...user, emailVerified: true } });

      expect(finished).toBeTrue();
      expect(auth.user()?.emailVerified).toBeTrue();
    });

    it('verifyEmail when signed out does not try to refresh a session', () => {
      let finished = false;
      auth.verifyEmail('tok-123').subscribe(() => (finished = true));
      http.expectOne(`${API}/auth/verify-email`).flush(null, { status: 204, statusText: 'No Content' });
      http.expectNone(`${API}/auth/refresh`);
      expect(finished).toBeTrue();
    });

    it('verifyEmail surfaces a rejected link to the caller', () => {
      let status: number | undefined;
      auth.verifyEmail('bad').subscribe({ error: (e) => (status = e.status) });
      http.expectOne(`${API}/auth/verify-email`).flush({ message: 'invalid' }, { status: 400, statusText: 'Bad Request' });
      expect(status).toBe(400);
    });

    it('resendVerification, forgotPassword and resetPassword call the right endpoints', () => {
      auth.resendVerification().subscribe();
      http.expectOne(`${API}/auth/resend-verification`).flush(null, { status: 204, statusText: 'No Content' });

      let message = '';
      auth.forgotPassword('maya@test.dev').subscribe((r) => (message = r.message));
      const forgot = http.expectOne(`${API}/auth/forgot-password`);
      expect(forgot.request.body).toEqual({ email: 'maya@test.dev' });
      forgot.flush({ message: 'If an account exists...' });
      expect(message).toContain('If an account exists');

      auth.resetPassword('tok', 'New-Pass-123').subscribe();
      const reset = http.expectOne(`${API}/auth/reset-password`);
      expect(reset.request.body).toEqual({ token: 'tok', newPassword: 'New-Pass-123' });
      reset.flush(null, { status: 204, statusText: 'No Content' });
    });
  });
});
