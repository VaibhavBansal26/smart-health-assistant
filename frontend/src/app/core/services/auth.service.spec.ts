import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { API_URL } from '../api.config';
import { User } from '../models/models';
import { AuthService } from './auth.service';
import { TokenStorage } from './token-storage.service';

const API = 'http://api.test';
const user: User = { id: 1, fullName: 'Maya Patel', email: 'maya@test.dev', role: 'PATIENT', specialty: null };
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
});
