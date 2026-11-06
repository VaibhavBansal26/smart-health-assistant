import { Injectable, Injector, computed, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { ChatStatus, ChatStreamEvent, ChatTurn } from '../models/models';
import { AuthService } from './auth.service';
import { ChatHttpError, ChatService } from './chat.service';

export interface ChatMsg {
  id: number;
  /** emergency/notice are UI-only messages produced by the server's safety layer. */
  role: 'user' | 'assistant' | 'emergency' | 'notice';
  text: string;
  streaming?: boolean;
}

const MAX_CONTENT = 2000; // server limit per message
const MAX_TURNS = 30;

/**
 * Chat state. Lives in memory only (cleared on logout, gone on refresh):
 * conversations are health data, so nothing is written to storage or the server.
 */
@Injectable({ providedIn: 'root' })
export class ChatStore {
  private readonly chat = inject(ChatService);
  // AuthService also depends on this store, so it is fetched lazily to avoid a construction cycle.
  private readonly injector = inject(Injector);

  private nextId = 1;
  private subscription?: Subscription;

  private readonly _messages = signal<ChatMsg[]>([]);
  private readonly _sending = signal(false);
  private readonly _error = signal<string | null>(null);

  readonly messages = this._messages.asReadonly();
  readonly sending = this._sending.asReadonly();
  readonly error = this._error.asReadonly();
  readonly status = signal<ChatStatus | null>(null);

  readonly hasUserMessages = computed(() => this._messages().some((m) => m.role === 'user'));

  loadStatus(): void {
    if (this.status()) return;
    this.chat.status().subscribe({ next: (s) => this.status.set(s), error: () => undefined });
  }

  send(text: string): void {
    if (this._sending()) return;
    this._messages.update((l) => [...l, { id: this.nextId++, role: 'user', text }]);
    this.run();
  }

  /** Re-asks after a failure without duplicating the user's message. */
  retry(): void {
    if (!this._sending()) this.run();
  }

  /** Stop button: keeps whatever has streamed so far. */
  stop(): void {
    this.subscription?.unsubscribe();
    this.finishStreaming();
  }

  reset(): void {
    this.subscription?.unsubscribe();
    this._messages.set([]);
    this._sending.set(false);
    this._error.set(null);
  }

  /** All the user's own words, used to prefill "Find a doctor". */
  userText(): string {
    return this._messages()
      .filter((m) => m.role === 'user')
      .map((m) => m.text)
      .join('. ');
  }

  private run(): void {
    const history: ChatTurn[] = this._messages()
      .filter((m) => (m.role === 'user' || m.role === 'assistant') && m.text.trim())
      .slice(-MAX_TURNS)
      .map((m) => ({ role: m.role as 'user' | 'assistant', content: m.text.slice(0, MAX_CONTENT) }));

    const id = this.nextId++;
    this._messages.update((l) => [...l, { id, role: 'assistant', text: '', streaming: true }]);
    this._sending.set(true);
    this._error.set(null);

    this.subscription = this.chat.stream(history).subscribe({
      next: (e) => this.handle(id, e),
      error: (err) => this.fail(id, err),
      complete: () => this.finishStreaming(),
    });
  }

  private handle(id: number, e: ChatStreamEvent): void {
    switch (e.type) {
      case 'delta':
        this.patch(id, (m) => ({ ...m, text: m.text + e.text }));
        break;
      case 'emergency':
        this.patch(id, (m) => ({ ...m, role: 'emergency', text: e.message, streaming: false }));
        break;
      case 'notice':
        this._messages.update((l) => [...l, { id: this.nextId++, role: 'notice', text: e.message }]);
        break;
      case 'error':
        this._error.set(e.message);
        break;
    }
  }

  private fail(id: number, err: unknown): void {
    const status = err instanceof ChatHttpError ? err.status : 0;
    if (status === 401) {
      this.injector.get(AuthService).logout('/chat');
      return;
    }
    this._error.set(err instanceof Error ? err.message : 'Something went wrong.');
    this.finishStreaming();
  }

  /** Marks streaming done and drops an assistant bubble that never received any text. */
  private finishStreaming(): void {
    this._messages.update((l) =>
      l
        .map((m) => (m.streaming ? { ...m, streaming: false } : m))
        .filter((m) => !(m.role === 'assistant' && !m.text)),
    );
    this._sending.set(false);
  }

  private patch(id: number, fn: (m: ChatMsg) => ChatMsg): void {
    this._messages.update((l) => l.map((m) => (m.id === id ? fn(m) : m)));
  }
}
