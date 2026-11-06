import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Observable, Subject, of } from 'rxjs';
import { ChatStatus, ChatStreamEvent, ChatTurn } from '../models/models';
import { AuthService } from './auth.service';
import { ChatHttpError, ChatService } from './chat.service';
import { ChatStore } from './chat.store';

class FakeChatService {
  events = new Subject<ChatStreamEvent>();
  histories: ChatTurn[][] = [];
  unsubscribed = false;
  status(): Observable<ChatStatus> {
    return of({ provider: 'demo', model: 'm' });
  }
  stream(history: ChatTurn[]): Observable<ChatStreamEvent> {
    this.histories.push(history);
    this.events = new Subject<ChatStreamEvent>();
    return new Observable((s) => {
      const sub = this.events.subscribe(s);
      return () => {
        this.unsubscribed = true;
        sub.unsubscribe();
      };
    });
  }
}

describe('ChatStore', () => {
  let store: ChatStore;
  let chat: FakeChatService;
  let logout: jasmine.Spy;

  beforeEach(() => {
    logout = jasmine.createSpy('logout');
    TestBed.configureTestingModule({
      providers: [
        { provide: ChatService, useClass: FakeChatService },
        { provide: AuthService, useValue: { logout } },
      ],
    });
    store = TestBed.inject(ChatStore);
    chat = TestBed.inject(ChatService) as unknown as FakeChatService;
    void TestBed.inject(Injector);
  });

  const roles = () => store.messages().map((m) => m.role);

  it('send() shows the user message and an empty, streaming assistant bubble', () => {
    store.send('I have a headache');
    expect(roles()).toEqual(['user', 'assistant']);
    expect(store.messages()[1].streaming).toBeTrue();
    expect(store.sending()).toBeTrue();
  });

  it('appends streamed chunks to the assistant message, then finishes on complete', () => {
    store.send('hi');
    chat.events.next({ type: 'delta', text: 'Hello ' });
    chat.events.next({ type: 'delta', text: 'there' });
    expect(store.messages()[1].text).toBe('Hello there');

    chat.events.next({ type: 'done' });
    chat.events.complete();
    expect(store.sending()).toBeFalse();
    expect(store.messages()[1].streaming).toBeFalse();
  });

  it('sends the whole conversation (but not UI-only messages) with each request', () => {
    store.send('first');
    chat.events.next({ type: 'delta', text: 'answer' });
    chat.events.complete();
    store.send('second');

    expect(chat.histories[1]).toEqual([
      { role: 'user', content: 'first' },
      { role: 'assistant', content: 'answer' },
      { role: 'user', content: 'second' },
    ]);
  });

  it('turns an emergency event into an emergency message and keeps it out of later history', () => {
    store.send('chest pain and short of breath');
    chat.events.next({ type: 'emergency', message: 'Call emergency services' });
    chat.events.complete();

    expect(roles()).toEqual(['user', 'emergency']);
    expect(store.messages()[1].text).toBe('Call emergency services');

    store.send('ok');
    expect(chat.histories[1].map((t) => t.role)).toEqual(['user', 'user']);
  });

  it('adds a notice message for server notices (e.g. truncated answer)', () => {
    store.send('hi');
    chat.events.next({ type: 'delta', text: 'partial' });
    chat.events.next({ type: 'notice', message: 'cut short' });
    chat.events.complete();
    expect(roles()).toEqual(['user', 'assistant', 'notice']);
  });

  it('an error before any text removes the empty bubble and exposes the message', () => {
    store.send('hi');
    chat.events.error(new ChatHttpError(0, 'Cannot reach the server.'));
    expect(roles()).toEqual(['user']);
    expect(store.error()).toBe('Cannot reach the server.');
    expect(store.sending()).toBeFalse();
  });

  it('retry() asks again WITHOUT duplicating the user message', () => {
    store.send('hi');
    chat.events.error(new ChatHttpError(500, 'failed'));
    store.retry();

    expect(store.messages().filter((m) => m.role === 'user').length).toBe(1);
    expect(chat.histories.length).toBe(2);
    expect(store.error()).toBeNull();
  });

  it('stop() aborts the stream but keeps what already arrived', () => {
    store.send('hi');
    chat.events.next({ type: 'delta', text: 'half an ans' });
    store.stop();

    expect(chat.unsubscribed).toBeTrue();
    expect(store.sending()).toBeFalse();
    expect(store.messages()[1].text).toBe('half an ans');
    expect(store.messages()[1].streaming).toBeFalse();
  });

  it('ignores a second send while one is in progress', () => {
    store.send('one');
    store.send('two');
    expect(store.messages().filter((m) => m.role === 'user').length).toBe(1);
  });

  it('a 401 logs the user out instead of showing an error', () => {
    store.send('hi');
    chat.events.error(new ChatHttpError(401, 'unauthorized'));
    expect(logout).toHaveBeenCalledWith('/chat');
  });

  it('userText() joins what the user said (used to prefill "Find a doctor")', () => {
    store.send('headache');
    chat.events.next({ type: 'delta', text: 'ok' });
    chat.events.complete();
    store.send('and nausea');
    expect(store.userText()).toBe('headache. and nausea');
  });

  it('reset() clears the conversation and aborts any stream', () => {
    store.send('hi');
    store.reset();
    expect(store.messages()).toEqual([]);
    expect(store.sending()).toBeFalse();
    expect(chat.unsubscribed).toBeTrue();
  });
});
