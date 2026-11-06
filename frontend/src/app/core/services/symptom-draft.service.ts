import { Injectable } from '@angular/core';

/**
 * Passes symptom text from the chat to "Find a doctor" in memory (single use),
 * never through the URL, because URLs end up in history and logs.
 */
@Injectable({ providedIn: 'root' })
export class SymptomDraftService {
  private text: string | null = null;

  set(text: string): void {
    this.text = text;
  }

  take(): string | null {
    const t = this.text;
    this.text = null;
    return t;
  }
}
