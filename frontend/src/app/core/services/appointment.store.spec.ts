import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_URL } from '../api.config';
import { Appointment, AppointmentSummary, Page } from '../models/models';
import { AppointmentStore } from './appointment.store';

const API = 'http://api.test';
const LIST = `${API}/appointments`;

const appt = (id: number, status: Appointment['status'] = 'BOOKED'): Appointment => ({
  id, patientId: 1, patientName: 'P', doctorId: 2, doctorName: 'Dr D', specialty: 'Cardiology',
  dateTime: '2030-01-01T10:00:00', reason: 'Checkup', status,
});
const page = (items: Appointment[], p = 0, totalPages = 1, total = items.length): Page<Appointment> => ({
  content: items, page: p, size: 10, totalElements: total, totalPages, hasNext: p + 1 < totalPages,
});

describe('AppointmentStore', () => {
  let store: AppointmentStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: API_URL, useValue: API }],
    });
    store = TestBed.inject(AppointmentStore);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  describe('list (server-side paging and filtering)', () => {
    it('asks the server for one page and exposes items and page info', () => {
      store.loadPage(0);
      expect(store.status()).toBe('loading');

      const req = http.expectOne((r) => r.url === LIST);
      expect(req.request.params.get('page')).toBe('0');
      expect(req.request.params.get('size')).toBe('10');
      expect(req.request.params.has('status')).toBeFalse();
      req.flush(page([appt(1), appt(2)], 0, 3, 25));

      expect(store.status()).toBe('loaded');
      expect(store.items().map((a) => a.id)).toEqual([1, 2]);
      expect(store.pageInfo()).toEqual({ page: 0, totalPages: 3, totalElements: 25 });
    });

    it('sends the status filter to the server and returns to the first page', () => {
      store.loadPage(2);
      http.expectOne((r) => r.url === LIST).flush(page([appt(1)], 2, 3));

      store.setFilter('CANCELLED');
      const req = http.expectOne((r) => r.url === LIST);
      expect(req.request.params.get('status')).toBe('CANCELLED');
      expect(req.request.params.get('page')).toBe('0');
      req.flush(page([]));
    });

    it('"ALL" sends no status parameter, and re-selecting the same filter makes no request', () => {
      store.setFilter('BOOKED');
      http.expectOne((r) => r.url === LIST).flush(page([]));
      store.setFilter('BOOKED'); // no-op: http.verify() in afterEach proves nothing was sent

      store.setFilter('ALL');
      expect(http.expectOne((r) => r.url === LIST).request.params.has('status')).toBeFalse();
    });

    it('ignores a slow, out-of-date response when a newer request was made', () => {
      store.loadPage(0);
      store.loadPage(1);
      const [first, second] = http.match((r) => r.url === LIST);

      second.flush(page([appt(20)], 1, 2));
      first.flush(page([appt(10)], 0, 2)); // arrives late: must not overwrite page 2

      expect(store.items().map((a) => a.id)).toEqual([20]);
      expect(store.pageInfo().page).toBe(1);
    });

    it('steps back a page when the current one became empty (e.g. last item deleted)', () => {
      store.loadPage(2);
      http.expectOne((r) => r.url === LIST).flush(page([], 2, 2, 20));

      const retry = http.expectOne((r) => r.url === LIST);
      expect(retry.request.params.get('page')).toBe('1');
      retry.flush(page([appt(5)], 1, 2, 20));
      expect(store.pageInfo().page).toBe(1);
    });

    it('shows the server error message and an error status', () => {
      store.loadPage(0);
      http.expectOne((r) => r.url === LIST).flush({ message: 'Boom' }, { status: 500, statusText: 'Server Error' });
      expect(store.status()).toBe('error');
      expect(store.error()).toBe('Boom');
    });

    it('a silent reload keeps showing the current page instead of a skeleton', () => {
      store.loadPage(0);
      http.expectOne((r) => r.url === LIST).flush(page([appt(1)]));

      store.loadPage(0, true);
      expect(store.status()).toBe('loaded');
      expect(store.items().length).toBe(1);
      http.expectOne((r) => r.url === LIST).flush(page([appt(1), appt(2)]));
      expect(store.items().length).toBe(2);
    });
  });

  describe('summary (dashboard)', () => {
    const summary: AppointmentSummary = { total: 5, booked: 2, completed: 2, cancelled: 1, next: appt(9) };

    it('has zero counts and no next appointment before loading', () => {
      expect(store.counts()).toEqual({ total: 0, booked: 0, completed: 0, cancelled: 0 });
      expect(store.next()).toBeNull();
    });

    it('exposes counts and the next appointment from the summary endpoint', () => {
      store.loadSummary();
      expect(store.summaryStatus()).toBe('loading');
      http.expectOne(`${LIST}/summary`).flush(summary);
      expect(store.summaryStatus()).toBe('loaded');
      expect(store.counts().booked).toBe(2);
      expect(store.next()?.id).toBe(9);
    });

    it('reports failures', () => {
      store.loadSummary();
      http.expectOne(`${LIST}/summary`).flush({ message: 'nope' }, { status: 500, statusText: 'x' });
      expect(store.summaryStatus()).toBe('error');
      expect(store.summaryError()).toBe('nope');
    });
  });

  describe('changes', () => {
    function loadEverything(): void {
      store.loadPage(0);
      http.expectOne((r) => r.url === LIST).flush(page([appt(1)]));
      store.loadSummary();
      http.expectOne(`${LIST}/summary`).flush({ total: 1, booked: 1, completed: 0, cancelled: 0, next: appt(1) });
    }

    it('cancel: PATCHes, marks the row busy meanwhile, then silently refreshes list and summary', () => {
      loadEverything();
      store.cancel(1).subscribe();
      expect(store.isBusy(1)).toBeTrue();

      http.expectOne(`${LIST}/1/cancel`).flush(appt(1, 'CANCELLED'));
      expect(store.isBusy(1)).toBeFalse();
      expect(store.status()).toBe('loaded'); // silent: no skeleton flash

      http.expectOne((r) => r.url === LIST).flush(page([appt(1, 'CANCELLED')]));
      http.expectOne(`${LIST}/summary`).flush({ total: 1, booked: 0, completed: 0, cancelled: 1, next: null });
      expect(store.counts().cancelled).toBe(1);
      expect(store.next()).toBeNull();
    });

    it('update: sends only the given fields as a PATCH to the appointment', () => {
      store.update(7, { dateTime: '2031-02-02T09:30' }).subscribe();
      const req = http.expectOne(`${LIST}/7`);
      expect(req.request.method).toBe('PATCH');
      expect(req.request.body).toEqual({ dateTime: '2031-02-02T09:30' });
      req.flush(appt(7));
    });

    it('create: POSTs and only refreshes what has been loaded before', () => {
      store.create({ doctorId: 2, dateTime: '2031-01-01T10:00', reason: 'x' }).subscribe();
      http.expectOne(LIST).flush(appt(3));
      // nothing was loaded, so no GET follows (http.verify() in afterEach checks that)
    });

    it('a failed change clears the busy flag and does not refresh', () => {
      loadEverything();
      store.complete(1).subscribe({ error: () => undefined });
      http.expectOne(`${LIST}/1/complete`).flush({ message: 'no' }, { status: 409, statusText: 'Conflict' });
      expect(store.isBusy(1)).toBeFalse();
    });
  });

  it('reset clears everything and ignores responses that were still in flight', () => {
    store.loadPage(0);
    store.loadSummary();
    const list = http.expectOne((r) => r.url === LIST);
    const sum = http.expectOne(`${LIST}/summary`);

    store.reset(); // e.g. user logged out
    list.flush(page([appt(1)]));
    sum.flush({ total: 9, booked: 9, completed: 0, cancelled: 0, next: null });

    expect(store.items()).toEqual([]);
    expect(store.status()).toBe('idle');
    expect(store.counts().total).toBe(0);
  });
});
