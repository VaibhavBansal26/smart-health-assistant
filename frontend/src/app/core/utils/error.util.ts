import { HttpErrorResponse } from '@angular/common/http';
import { ApiErrorBody } from '../models/models';

export interface ParsedError {
  status: number;
  message: string;
  fieldErrors: Record<string, string>;
}

/**
 * Turns anything thrown by HttpClient into one predictable shape so every
 * component can show errors the same way.
 */
export function parseApiError(err: unknown): ParsedError {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return {
        status: 0,
        message: 'Cannot reach the server. Check your connection and try again.',
        fieldErrors: {},
      };
    }
    const body = err.error as Partial<ApiErrorBody> | null;
    const fromServer = body && typeof body === 'object' ? body.message : undefined;
    return {
      status: err.status,
      message: fromServer ?? (err.status >= 500 ? 'Something went wrong on our side.' : 'Request failed.'),
      fieldErrors: body?.fieldErrors ?? {},
    };
  }
  return { status: -1, message: 'Unexpected error.', fieldErrors: {} };
}
