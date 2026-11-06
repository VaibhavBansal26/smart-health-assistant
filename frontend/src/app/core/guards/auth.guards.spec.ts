import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { Role } from '../models/models';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';
import { authGuard, guestGuard, roleGuard } from './auth.guards';

describe('route guards', () => {
  const role = signal<Role | null>(null);
  const loggedIn = signal(false);
  let toast: jasmine.SpyObj<ToastService>;

  beforeEach(() => {
    toast = jasmine.createSpyObj('ToastService', ['error']);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { isLoggedIn: loggedIn, role } },
        { provide: ToastService, useValue: toast },
      ],
    });
  });

  const route = {} as ActivatedRouteSnapshot;
  const state = (url: string) => ({ url }) as RouterStateSnapshot;
  const run = (guard: typeof authGuard, url = '/x') => TestBed.runInInjectionContext(() => guard(route, state(url)));
  const urlOf = (result: unknown) => TestBed.inject(Router).serializeUrl(result as UrlTree);

  it('authGuard sends anonymous users to /login and remembers where they wanted to go', () => {
    loggedIn.set(false);
    expect(urlOf(run(authGuard, '/appointments?x=1'))).toBe('/login?returnUrl=%2Fappointments%3Fx%3D1');
  });

  it('authGuard lets signed-in users through', () => {
    loggedIn.set(true);
    expect(run(authGuard)).toBeTrue();
  });

  it('guestGuard keeps signed-in users away from login/register', () => {
    loggedIn.set(true);
    expect(urlOf(run(guestGuard))).toBe('/dashboard');
    loggedIn.set(false);
    expect(run(guestGuard)).toBeTrue();
  });

  it('roleGuard allows only the given role, otherwise redirects with a message', () => {
    const guard = roleGuard('PATIENT');
    role.set('PATIENT');
    expect(run(guard)).toBeTrue();

    role.set('DOCTOR');
    expect(urlOf(run(guard))).toBe('/dashboard');
    expect(toast.error).toHaveBeenCalled();
  });
});
