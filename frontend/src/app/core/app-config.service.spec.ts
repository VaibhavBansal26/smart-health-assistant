import { TestBed } from '@angular/core/testing';
import { AppConfigService } from './app-config.service';

describe('AppConfigService', () => {
  let service: AppConfigService;
  let fetchSpy: jasmine.Spy;

  beforeEach(() => {
    service = TestBed.inject(AppConfigService);
    fetchSpy = spyOn(window, 'fetch');
    spyOn(console, 'warn');
  });

  const respond = (body: unknown, ok = true) =>
    fetchSpy.and.resolveTo({ ok, status: ok ? 200 : 404, json: async () => body } as Response);

  it('uses the apiUrl from config.json, trimming trailing slashes', async () => {
    respond({ apiUrl: 'https://api.example.com/api//' });
    await service.load();
    expect(service.apiUrl).toBe('https://api.example.com/api');
  });

  it('falls back to the local default when config.json is missing', async () => {
    respond({}, false);
    await service.load();
    expect(service.apiUrl).toBe('http://localhost:8080/api');
    expect(console.warn).toHaveBeenCalled();
  });

  it('falls back when the file is malformed or the request fails', async () => {
    fetchSpy.and.rejectWith(new Error('offline'));
    await service.load();
    expect(service.apiUrl).toBe('http://localhost:8080/api');

    respond({ apiUrl: '   ' });
    await service.load();
    expect(service.apiUrl).toBe('http://localhost:8080/api');
  });

  it('never reads the file from the HTTP cache', async () => {
    respond({ apiUrl: 'https://x/api' });
    await service.load();
    expect(fetchSpy).toHaveBeenCalledWith('config.json', { cache: 'no-store' });
  });
});
