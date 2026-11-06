import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { API_URL } from '../api.config';
import { TokenStorage } from '../services/token-storage.service';

/** Endpoints that use the refresh cookie rather than (only) the bearer token. */
const AUTH_ENDPOINTS = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout'];
const COOKIE_ENDPOINTS = ['/auth/refresh', '/auth/logout'];

/**
 * For calls to OUR API only (the token is never sent to third-party URLs):
 *  - adds "Authorization: Bearer <access token>"
 *  - on the auth endpoints, lets the browser send/receive the HttpOnly refresh cookie (withCredentials)
 *  - on refresh/logout, adds the X-Requested-With header the backend requires (cross-site forms cannot set it)
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const api = inject(API_URL);
  if (!req.url.startsWith(api)) {
    return next(req);
  }
  const token = inject(TokenStorage).get();
  const path = req.url.slice(api.length);
  const isAuth = AUTH_ENDPOINTS.some((e) => path.startsWith(e));
  const needsHeader = COOKIE_ENDPOINTS.some((e) => path.startsWith(e));

  return next(
    req.clone({
      withCredentials: isAuth || req.withCredentials,
      setHeaders: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(needsHeader ? { 'X-Requested-With': 'XMLHttpRequest' } : {}),
      },
    }),
  );
};
