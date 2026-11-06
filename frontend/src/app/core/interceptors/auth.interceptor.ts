import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { API_URL } from '../api.config';
import { TokenStorage } from '../services/token-storage.service';

/** Adds "Authorization: Bearer <jwt>" to every request that goes to our API. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(TokenStorage).get();
  const api = inject(API_URL);

  // never leak the token to third-party URLs
  if (token && req.url.startsWith(api)) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req);
};
