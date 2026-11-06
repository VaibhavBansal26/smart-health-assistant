import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_URL } from '../api.config';
import { AdminDoctorService } from './admin-doctor.service';

const API = 'http://api.test';

describe('AdminDoctorService', () => {
  let service: AdminDoctorService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: API_URL, useValue: API }],
    });
    service = TestBed.inject(AdminDoctorService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('lists with paging by default and no filters', () => {
    service.list({}).subscribe();
    const req = http.expectOne((r) => r.url === `${API}/admin/doctors`);
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.has('q')).toBeFalse();
    expect(req.request.params.has('active')).toBeFalse();
    req.flush({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, hasNext: false });
  });

  it('sends the search text (trimmed) and the active filter, including false', () => {
    service.list({ q: '  verma ', active: false, page: 2 }).subscribe();
    const req = http.expectOne((r) => r.url === `${API}/admin/doctors`);
    expect(req.request.params.get('q')).toBe('verma');
    expect(req.request.params.get('active')).toBe('false');
    expect(req.request.params.get('page')).toBe('2');
    req.flush({});
  });

  it('creates, updates (PATCH) and re-invites', () => {
    service.create({ fullName: 'Dr. A', email: 'a@x.test', specialty: 'ENT' }).subscribe();
    const create = http.expectOne(`${API}/admin/doctors`);
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual({ fullName: 'Dr. A', email: 'a@x.test', specialty: 'ENT' });
    create.flush({});

    service.update(7, { active: false }).subscribe();
    const update = http.expectOne(`${API}/admin/doctors/7`);
    expect(update.request.method).toBe('PATCH');
    expect(update.request.body).toEqual({ active: false });
    update.flush({});

    service.resendInvite(7).subscribe();
    const invite = http.expectOne(`${API}/admin/doctors/7/resend-invite`);
    expect(invite.request.method).toBe('POST');
    invite.flush(null, { status: 204, statusText: 'No Content' });
  });
});
