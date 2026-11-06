import { Injectable } from '@angular/core';

export interface AppConfig {
  /** Base URL of the backend API, e.g. https://api.example.com/api */
  apiUrl: string;
}

const DEFAULTS: AppConfig = { apiUrl: 'http://localhost:8080/api' };

/**
 * Runtime configuration, read once at startup from /config.json (see public/config.json).
 *
 * Angular bakes environment files into the bundle at build time, which would mean one build per
 * environment. Loading a small JSON file instead lets the SAME build run anywhere: a deployment
 * just replaces config.json (for example by generating it from environment variables in the
 * container entrypoint). Nothing secret belongs here: it is downloaded by every visitor.
 */
@Injectable({ providedIn: 'root' })
export class AppConfigService {
  private config: AppConfig = DEFAULTS;

  get apiUrl(): string {
    return this.config.apiUrl;
  }

  async load(): Promise<void> {
    try {
      const res = await fetch('config.json', { cache: 'no-store' });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const body = (await res.json()) as Partial<AppConfig>;
      if (typeof body.apiUrl === 'string' && body.apiUrl.trim()) {
        this.config = { apiUrl: body.apiUrl.trim().replace(/\/+$/, '') };
      }
    } catch (err) {
      console.warn('config.json could not be loaded; using the default API URL', DEFAULTS.apiUrl, err);
    }
  }
}
