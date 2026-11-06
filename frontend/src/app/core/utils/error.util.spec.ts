import { HttpErrorResponse } from '@angular/common/http';
import { parseApiError } from './error.util';

describe('parseApiError', () => {
  const http = (status: number, error: unknown) => new HttpErrorResponse({ status, error });

  it('uses the server message and field errors', () => {
    const e = parseApiError(http(400, { message: 'Validation failed', fieldErrors: { age: 'too young' } }));
    expect(e).toEqual({ status: 400, message: 'Validation failed', fieldErrors: { age: 'too young' } });
  });

  it('explains a network failure (status 0) in plain words', () => {
    expect(parseApiError(http(0, null)).message).toContain('Cannot reach the server');
  });

  it('hides server internals for 5xx without a body message', () => {
    expect(parseApiError(http(500, '<html>stack trace</html>')).message).toBe('Something went wrong on our side.');
  });

  it('falls back to a generic message for 4xx without a body message', () => {
    expect(parseApiError(http(404, null)).message).toBe('Request failed.');
  });

  it('copes with non-HTTP errors', () => {
    expect(parseApiError(new Error('x')).message).toBe('Unexpected error.');
    expect(parseApiError('weird').status).toBe(-1);
  });
});
