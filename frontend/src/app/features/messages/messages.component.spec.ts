import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_URL } from '../../core/api.config';
import { ChatMessage, MessageThread } from '../../core/models/models';
import { MessagesComponent, POLL_MS } from './messages.component';

const API = 'http://api.test';
const URL = `${API}/appointments/7/messages`;

const msg = (id: number, over: Partial<ChatMessage> = {}): ChatMessage => ({
  id, senderId: 1, senderName: 'Nora Vega', mine: false, body: `message ${id}`, createdAt: '2026-11-07T10:00:00Z', ...over,
});
const thread = (messages: ChatMessage[], over: Partial<MessageThread> = {}): MessageThread => ({
  appointmentId: 7, withName: 'Dr. Asha Verma', withRole: 'DOCTOR', status: 'BOOKED', dateTime: '2026-11-08T10:00:00', canSend: true, messages, ...over,
});

describe('MessagesComponent', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<MessagesComponent>;
  let root: HTMLElement;

  const gets = () => http.match((r) => r.url === URL && r.method === 'GET');
  /** Leaves the page (stops the polling timer) so no request is left open. */
  const end = () => { fixture.destroy(); flush(); };
  const textarea = () => root.querySelector('textarea') as HTMLTextAreaElement;
  const type = (value: string) => { textarea().value = value; textarea().dispatchEvent(new Event('input')); fixture.detectChanges(); };

  function start(first: MessageThread | HttpErrorResponse = thread([msg(1), msg(2, { mine: true })])) {
    spyOnProperty(document, 'hidden').and.returnValue(false);
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: API_URL, useValue: API }],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MessagesComponent);
    fixture.componentRef.setInput('id', '7');
    root = fixture.nativeElement;
    fixture.detectChanges();
    const req = gets()[0];
    if (first instanceof HttpErrorResponse) req.flush({ message: first.error.message }, { status: first.status, statusText: 'x' });
    else req.flush(first);
    fixture.detectChanges();
  }
  afterEach(() => { http.verify(); });

  it('shows who the conversation is with and the existing messages, marking mine', fakeAsync(() => {
    start();
    expect(root.querySelector('h1')!.textContent).toContain('Dr. Asha Verma');
    expect(root.querySelectorAll('.bubble').length).toBe(2);
    expect(root.querySelectorAll('.bubble.mine').length).toBe(1);
    end();
  }));

  it('shows plain text, never HTML, from the other person', fakeAsync(() => {
    start(thread([msg(1, { body: '<img src=x onerror=alert(1)>' })]));
    expect(root.querySelector('.bubble img')).toBeNull();
    expect(root.querySelector('.bubble')!.textContent).toContain('<img src=x');
    end();
  }));

  it('sends on Enter, clears the box and appends the saved message', fakeAsync(() => {
    start(thread([]));
    expect(root.textContent).toContain('No messages yet');
    type('  Hello doctor  ');
    textarea().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    const post = http.expectOne((r) => r.url === URL && r.method === 'POST');
    expect(post.request.body).toEqual({ body: 'Hello doctor' });
    post.flush(msg(5, { mine: true, body: 'Hello doctor' }));
    fixture.detectChanges();
    expect(textarea().value).toBe('');
    expect(root.querySelectorAll('.bubble').length).toBe(1);
    end();
  }));

  it('does not send blank text', fakeAsync(() => {
    start(thread([]));
    type('   ');
    expect(root.querySelector<HTMLButtonElement>('button[type=submit]')!.disabled).toBeTrue();
    end();
  }));

  it('keeps the draft and shows the reason when sending fails', fakeAsync(() => {
    start(thread([]));
    type('please keep me');
    root.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    http.expectOne((r) => r.method === 'POST').flush({ message: 'Too long' }, { status: 400, statusText: 'Bad' });
    fixture.detectChanges();
    expect(textarea().value).toBe('please keep me');
    expect(root.querySelector('.alert.error')!.textContent).toContain('Too long');
    end();
  }));

  it('polls for newer messages using the last seen id and de-duplicates', fakeAsync(() => {
    start(thread([msg(1), msg(2)]));
    tick(POLL_MS);
    const poll = gets()[0];
    expect(poll.request.params.get('after')).toBe('2');
    poll.flush(thread([msg(2), msg(3, { body: 'new one' })]));
    fixture.detectChanges();
    expect(root.querySelectorAll('.bubble').length).toBe(3);
    expect(root.textContent).toContain('new one');
    end();
  }));

  it('ignores a failed poll and tries again on the next tick', fakeAsync(() => {
    start(thread([msg(1)]));
    tick(POLL_MS);
    gets()[0].error(new ProgressEvent('error'));
    tick(POLL_MS);
    gets()[0].flush(thread([msg(2)]));
    fixture.detectChanges();
    expect(root.querySelectorAll('.bubble').length).toBe(2);
    end();
  }));

  it('is read-only for a cancelled appointment', fakeAsync(() => {
    start(thread([msg(1)], { status: 'CANCELLED', canSend: false }));
    expect(textarea()).toBeNull();
    expect(root.textContent).toContain('read-only');
    end();
  }));

  it('offers a retry when the thread cannot be loaded', fakeAsync(() => {
    start(new HttpErrorResponse({ status: 404, error: { message: 'Appointment not found' } }));
    expect(root.querySelector('.alert.error')!.textContent).toContain('Appointment not found');
    root.querySelector<HTMLButtonElement>('button')!.click();
    gets()[0].flush(thread([msg(1)]));
    fixture.detectChanges();
    expect(root.querySelectorAll('.bubble').length).toBe(1);
    end();
  }));

  it('stops polling when the page is left', fakeAsync(() => {
    start(thread([msg(1)]));
    fixture.destroy();
    tick(POLL_MS * 3);
    expect(gets().length).toBe(0);
  }));
});
