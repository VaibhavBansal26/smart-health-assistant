import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  Injector,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { firstValueFrom } from 'rxjs';

import { routes } from './app.routes';
import { API_URL } from './core/api.config';
import { AppConfigService } from './core/app-config.service';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { AuthService } from './core/services/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),

    // withComponentInputBinding: ?returnUrl=... in the URL becomes a component input()
    provideRouter(routes, withComponentInputBinding()),

    // Interceptors run in this order on the way out (and reverse on the way back)
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),

    // the API URL comes from /config.json at runtime (see AppConfigService), not from the build
    { provide: API_URL, useFactory: () => inject(AppConfigService).apiUrl },

    // Before the first screen renders: load runtime config, THEN restore the session (validate the saved token).
    // Order matters: services read API_URL when they are created, so AuthService must not exist before the config does.
    provideAppInitializer(async () => {
      const injector = inject(Injector); // inject() only works before the first await
      await injector.get(AppConfigService).load();
      await firstValueFrom(injector.get(AuthService).restoreSession());
    }),
  ],
};
