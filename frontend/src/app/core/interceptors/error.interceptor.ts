import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';

const AUTH_URLS = ['/auth/login', '/auth/register'];

/**
 * One place for cross-cutting HTTP failures:
 *  - 401 anywhere except login/register => session expired => back to login
 *  - unexpected 5xx => generic toast (components don't each need to handle it)
 * The error is always re-thrown so a component can still react to it.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  // inject() only works here, synchronously - not inside the callbacks below
  const auth = inject(AuthService);
  const toast = inject(ToastService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse) {
        const isAuthCall = AUTH_URLS.some((u) => req.url.includes(u));
        if (err.status === 401 && !isAuthCall) {
          if (auth.isLoggedIn()) {
            toast.info('Your session expired. Please sign in again.');
          }
          auth.logout(router.url.startsWith('/login') ? undefined : router.url);
        } else if (err.status >= 500 && err.status !== 503) {
          // 503 is a deliberate "dependency down" answer that screens explain inline
          toast.error('Server error. Please try again in a moment.');
        }
      }
      return throwError(() => err);
    }),
  );
};
