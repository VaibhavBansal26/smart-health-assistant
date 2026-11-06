import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';
import { TokenStorage } from '../services/token-storage.service';

const AUTH_URLS = [
  '/auth/login', '/auth/register', '/auth/refresh', '/auth/logout',
  '/auth/verify-email', '/auth/forgot-password', '/auth/reset-password',
];

/**
 * One place for cross-cutting HTTP failures:
 *  - 401 on a normal API call: the short-lived access token probably expired. Silently get a new one with the refresh
 *    cookie and replay the request ONCE. Only if that fails is the user signed out and sent to the login page.
 *    Several requests failing at once share a single refresh (see AuthService.refresh).
 *  - unexpected 5xx => generic toast (components don't each need to handle it)
 * The error is always re-thrown so a component can still react to it.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  // inject() only works here, synchronously - not inside the callbacks below
  const auth = inject(AuthService);
  const toast = inject(ToastService);
  const router = inject(Router);
  const tokens = inject(TokenStorage);

  const endSession = () => {
    if (auth.isLoggedIn()) {
      toast.info('Your session expired. Please sign in again.');
    }
    auth.logout(router.url.startsWith('/login') ? undefined : router.url);
  };

  return next(req).pipe(
    catchError((err: unknown) => {
      if (!(err instanceof HttpErrorResponse)) {
        return throwError(() => err);
      }
      const isAuthCall = AUTH_URLS.some((u) => req.url.includes(u));

      if (err.status === 401 && !isAuthCall) {
        return auth.refresh().pipe(
          switchMap((refreshed) => {
            if (!refreshed) {
              endSession();
              return throwError(() => err);
            }
            // replay with the NEW token (this interceptor sits after authInterceptor, so set it ourselves)
            return next(req.clone({ setHeaders: { Authorization: `Bearer ${tokens.get()}` } })).pipe(
              catchError((retryErr: unknown) => {
                if (retryErr instanceof HttpErrorResponse && retryErr.status === 401) {
                  endSession();
                }
                return throwError(() => retryErr);
              }),
            );
          }),
        );
      }
      if (err.status >= 500 && err.status !== 503) {
        // 503 is a deliberate "dependency down" answer that screens explain inline
        toast.error('Server error. Please try again in a moment.');
      }
      return throwError(() => err);
    }),
  );
};
