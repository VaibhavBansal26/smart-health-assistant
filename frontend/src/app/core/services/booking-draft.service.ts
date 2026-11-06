import { Injectable, signal } from '@angular/core';

export interface BookingDraft {
  doctorId: number;
  reason: string;
}

/**
 * Hands a half-filled booking from "Find a doctor" to the booking form.
 *
 * It is deliberately in-memory and NOT in the URL: symptom text is health
 * data, and URLs end up in browser history, proxy logs and referrer headers.
 * The draft is single-use: `take()` returns it and clears it.
 */
@Injectable({ providedIn: 'root' })
export class BookingDraftService {
  private readonly draft = signal<BookingDraft | null>(null);

  set(draft: BookingDraft): void {
    this.draft.set(draft);
  }

  take(): BookingDraft | null {
    const d = this.draft();
    this.draft.set(null);
    return d;
  }
}
