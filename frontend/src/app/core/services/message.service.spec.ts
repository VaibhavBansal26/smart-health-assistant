import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_URL } from '../api.config';
import { MessageService } from './message.service';

const API = 'http://api.test';

describe('MessageService', () => {
  let service: MessageService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: API_URL, useValue: API }],
    });
    service = TestBed.inject(MessageService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('reads a thread, passing the last seen id as "after"', () => {
    service.thread(7, 42).subscribe();
    const req = http.expectOne((r) => r.url === `${API}/appointments/7/messages`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('after')).toBe('42');
    req.flush({});
  });

  it('starts from the beginning by default', () => {
    service.thread(7).subscribe();
    const req = http.expectOne((r) => r.url === `${API}/appointments/7/messages`);
    expect(req.request.params.get('after')).toBe('0');
    req.flush({});
  });

  it('posts the message body', () => {
    service.send(7, 'hello').subscribe();
    const req = http.expectOne(`${API}/appointments/7/messages`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ body: 'hello' });
    req.flush({});
  });
});
