import { InjectionToken } from '@angular/core';

/**
 * Base URL of the Spring Boot API. Provided once in app.config.ts so services
 * never hard-code it (and tests can swap it out).
 */
export const API_URL = new InjectionToken<string>('API_URL');
