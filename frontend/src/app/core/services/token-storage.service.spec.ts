import { TestBed } from '@angular/core/testing';
import { TokenStorage } from './token-storage.service';

describe('TokenStorage', () => {
  let storage: TokenStorage;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    storage = TestBed.inject(TokenStorage);
  });

  it('keeps the access token in memory only: nothing sensitive reaches web storage', () => {
    storage.set('super-secret-access-token');
    expect(storage.get()).toBe('super-secret-access-token');

    const everythingStored = JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage });
    expect(everythingStored).not.toContain('super-secret-access-token');
  });

  it('remembers only a boolean hint that a session existed, and forgets it on clear()', () => {
    expect(storage.hasSessionHint()).toBeFalse();
    storage.set('t');
    expect(storage.hasSessionHint()).toBeTrue();
    expect(localStorage.getItem('health.signedIn')).toBe('1');

    storage.clear();
    expect(storage.get()).toBeNull();
    expect(storage.hasSessionHint()).toBeFalse();
  });

  it('a new instance (a page reload) has no token, only the hint', () => {
    storage.set('t');
    TestBed.resetTestingModule();
    const reloaded = TestBed.inject(TokenStorage);
    expect(reloaded.get()).toBeNull();
    expect(reloaded.hasSessionHint()).toBeTrue();
  });

  it('still works when web storage is unavailable (private mode)', () => {
    spyOn(Storage.prototype, 'getItem').and.throwError('denied');
    spyOn(Storage.prototype, 'setItem').and.throwError('denied');
    expect(() => storage.set('t')).not.toThrow();
    expect(storage.get()).toBe('t');
    expect(storage.hasSessionHint()).toBeFalse();
  });
});
