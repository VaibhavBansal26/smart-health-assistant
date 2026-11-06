import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api.config';
import { ChatStatus, ChatStreamEvent, ChatTurn } from '../models/models';
import { TokenStorage } from './token-storage.service';

/** Thrown when the server rejects the request before streaming starts (401, 400, 429...). */
export class ChatHttpError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_URL);
  private readonly tokens = inject(TokenStorage);

  status(): Observable<ChatStatus> {
    return this.http.get<ChatStatus>(`${this.api}/chat/status`);
  }

  /**
   * Streams a reply as an Observable of events.
   *
   * Why fetch() and not HttpClient/EventSource? EventSource cannot send a POST body
   * or an Authorization header, and HttpClient only emits once the whole body arrived.
   * fetch() exposes the response as a stream we can read chunk by chunk.
   * Unsubscribing aborts the request (that is how the Stop button works).
   */
  stream(messages: ChatTurn[]): Observable<ChatStreamEvent> {
    return new Observable<ChatStreamEvent>((subscriber) => {
      const abort = new AbortController();

      (async () => {
        try {
          const token = this.tokens.get();
          const res = await fetch(`${this.api}/chat/stream`, {
            method: 'POST',
            signal: abort.signal,
            headers: {
              'Content-Type': 'application/json',
              Accept: 'text/event-stream',
              ...(token ? { Authorization: `Bearer ${token}` } : {}),
            },
            body: JSON.stringify({ messages }),
          });

          if (!res.ok || !res.body) {
            // errors before the stream starts are normal JSON bodies
            const body = await res.json().catch(() => null);
            subscriber.error(new ChatHttpError(res.status, body?.message ?? 'The request failed.'));
            return;
          }

          const reader = res.body.getReader();
          const decoder = new TextDecoder();
          let buffer = '';
          for (;;) {
            const { done, value } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            // SSE frames are separated by a blank line
            let end: number;
            while ((end = buffer.indexOf('\n\n')) >= 0) {
              const event = parseFrame(buffer.slice(0, end));
              buffer = buffer.slice(end + 2);
              if (event) subscriber.next(event);
            }
          }
          subscriber.complete();
        } catch (err) {
          if (abort.signal.aborted) return; // user pressed Stop: not an error
          subscriber.error(
            new ChatHttpError(0, 'Cannot reach the server. Check your connection and try again.'),
          );
          void err;
        }
      })();

      return () => abort.abort();
    });
  }
}

function parseFrame(frame: string): ChatStreamEvent | null {
  let name = 'message';
  const data: string[] = [];
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) name = line.slice(6).trim();
    else if (line.startsWith('data:')) data.push(line.slice(5).trimStart());
  }
  if (name === 'done') return { type: 'done' };
  try {
    const payload = JSON.parse(data.join('\n')) as { text?: string; message?: string };
    switch (name) {
      case 'delta':
        return { type: 'delta', text: payload.text ?? '' };
      case 'emergency':
      case 'notice':
      case 'error':
        return { type: name, message: payload.message ?? '' };
    }
  } catch {
    /* ignore a malformed frame rather than killing the whole chat */
  }
  return null;
}
