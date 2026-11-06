import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { API_URL } from '../api.config';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';
import { TokenStorage } from '../services/token-storage.service';
import { authInterceptor } from './auth.interceptor';
import { errorInterceptor } from './error.interceptor';

const API = 'http://api.test';
const user = { id: 1, fullName: 'Maya', email: 'm@test.dev', role: 'PATIENT', specialty: null };
const unauthorized = { status: 401, statusText: 'Unauthorized' };

describe('HTTP interceptors', () => {
  let http: HttpClient;
  let ctrl: HttpTestingController;
  let storage: TokenStorage;
  let navigate: jasmine.Spy;
  let toast: ToastService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: API },
      ],
    });
    http = TestBed.inject(HttpClient);
    ctrl = TestBed.inject(HttpTestingController);
    storage = TestBed.inject(TokenStorage);
    toast = TestBed.inject(ToastService);
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
  });
  afterEach(() => ctrl.verify());

  describe('authInterceptor', () => {
    it('adds the bearer token to API calls, and never to other hosts', () => {
      storage.set('abc');
      http.get(`${API}/doctors`).subscribe();
      expect(ctrl.expectOne(`${API}/doctors`).request.headers.get('Authorization')).toBe('Bearer abc');

      http.get('https://other.example/data').subscribe();
      expect(ctrl.expectOne('https://other.example/data').request.headers.has('Authorization')).toBeFalse();
    });

    it('sends no Authorization header when signed out', () => {
      http.get(`${API}/doctors`).subscribe();
      expect(ctrl.expectOne(`${API}/doctors`).request.headers.has('Authorization')).toBeFalse();
    });

    it('lets the cookie travel only on auth endpoints, and adds the CSRF header only for refresh/logout', () => {
      http.post(`${API}/auth/login`, {}).subscribe();
      const login = ctrl.expectOne(`${API}/auth/login`).request;
      expect(login.withCredentials).toBeTrue();
      expect(login.headers.has('X-Requested-With')).toBeFalse();

      http.post(`${API}/auth/refresh`, {}).subscribe();
      const refresh = ctrl.expectOne(`${API}/auth/refresh`).request;
      expect(refresh.withCredentials).toBeTrue();
      expect(refresh.headers.get('X-Requested-With')).toBe('XMLHttpRequest');

      http.get(`${API}/doctors`).subscribe();
      const ordinary = ctrl.expectOne(`${API}/doctors`).request;
      expect(ordinary.withCredentials).toBeFalse();
      expect(ordinary.headers.has('X-Requested-With')).toBeFalse();
    });
  });

  describe('errorInterceptor: silent refresh on 401', () => {
    beforeEach(() => storage.set('expired-access-token'));

    it('refreshes once and replays the request with the NEW token, invisibly to the caller', () => {
      let result: unknown;
      http.get(`${API}/doctors`).subscribe((r) => (result = r));

      ctrl.expectOne(`${API}/doctors`).flush({}, unauthorized);
      ctrl.expectOne(`${API}/auth/refresh`).flush({ token: 'new-token', expiresInSeconds: 900, user });
      const retry = ctrl.expectOne(`${API}/doctors`);
      expect(retry.request.headers.get('Authorization')).toBe('Bearer new-token');
      retry.flush({ content: ['ok'] });

      expect(result).toEqual({ content: ['ok'] });
      expect(navigate).not.toHaveBeenCalled();
    });

    it('several requests failing together share ONE refresh, then all are replayed', () => {
      const results: unknown[] = [];
      http.get(`${API}/a`).subscribe((r) => results.push(r));
      http.get(`${API}/b`).subscribe((r) => results.push(r));
      http.get(`${API}/c`).subscribe((r) => results.push(r));

      for (const path of ['a', 'b', 'c']) ctrl.expectOne(`${API}/${path}`).flush({}, unauthorized);
      const refreshes = ctrl.match(`${API}/auth/refresh`);
      expect(refreshes.length).toBe(1);
      refreshes[0].flush({ token: 'new-token', expiresInSeconds: 900, user });

      for (const path of ['a', 'b', 'c']) ctrl.expectOne(`${API}/${path}`).flush(path);
      expect(results.sort()).toEqual(['a', 'b', 'c']);
    });

    it('when the refresh is refused, signs the user out and still reports the original 401', () => {
      let status: number | undefined;
      http.get(`${API}/doctors`).subscribe({ error: (e) => (status = e.status) });

      ctrl.expectOne(`${API}/doctors`).flush({}, unauthorized);
      ctrl.expectOne(`${API}/auth/refresh`).flush({ message: 'expired' }, unauthorized);
      ctrl.expectOne(`${API}/auth/logout`).flush(null, { status: 204, statusText: 'No Content' });

      expect(status).toBe(401);
      expect(navigate).toHaveBeenCalled();
      expect(navigate.calls.mostRecent().args[0]).toEqual(['/login']);
    });

    it('if the replayed request is STILL 401, it gives up and signs out (no endless loop)', () => {
      let status: number | undefined;
      http.get(`${API}/doctors`).subscribe({ error: (e) => (status = e.status) });

      ctrl.expectOne(`${API}/doctors`).flush({}, unauthorized);
      ctrl.expectOne(`${API}/auth/refresh`).flush({ token: 'new', expiresInSeconds: 900, user });
      ctrl.expectOne(`${API}/doctors`).flush({}, unauthorized);
      ctrl.expectOne(`${API}/auth/logout`).flush(null, { status: 204, statusText: 'No Content' });

      expect(status).toBe(401);
      expect(navigate).toHaveBeenCalled();
    });

    it('a 401 from login/register/refresh themselves is never retried (wrong password stays wrong)', () => {
      http.post(`${API}/auth/login`, {}).subscribe({ error: () => undefined });
      ctrl.expectOne(`${API}/auth/login`).flush({}, unauthorized);
      ctrl.expectNone(`${API}/auth/refresh`);
      expect(navigate).not.toHaveBeenCalled();
    });

    it('other errors pass straight through without touching the session', () => {
      let status: number | undefined;
      http.get(`${API}/doctors`).subscribe({ error: (e) => (status = e.status) });
      ctrl.expectOne(`${API}/doctors`).flush({}, { status: 409, statusText: 'Conflict' });
      expect(status).toBe(409);
      ctrl.expectNone(`${API}/auth/refresh`);
    });
  });

  describe('errorInterceptor: server errors', () => {
    it('toasts unexpected 5xx, but not the deliberate 503 that screens explain themselves', () => {
      const spy = spyOn(toast, 'error');
      http.get(`${API}/x`).subscribe({ error: () => undefined });
      ctrl.expectOne(`${API}/x`).flush({}, { status: 500, statusText: 'Error' });
      expect(spy).toHaveBeenCalledTimes(1);

      http.get(`${API}/y`).subscribe({ error: () => undefined });
      ctrl.expectOne(`${API}/y`).flush({}, { status: 503, statusText: 'Unavailable' });
      expect(spy).toHaveBeenCalledTimes(1);
    });
  });

  it('AuthService is the single source of refreshes (sanity: it is the real service wired in)', () => {
    expect(TestBed.inject(AuthService)).toBeTruthy();
  });
});
