import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_URL } from '../../core/api.config';
import { AdminDoctor, Page } from '../../core/models/models';
import { AdminDoctorsComponent } from './admin-doctors.component';

const API = 'http://api.test';
const LIST = `${API}/admin/doctors`;

const doctor = (id: number, over: Partial<AdminDoctor> = {}): AdminDoctor => ({
  id, fullName: `Dr. Number ${id}`, email: `d${id}@clinic.test`, specialty: 'Cardiology', active: true, setupPending: false, ...over,
});
const page = (items: AdminDoctor[], p = 0, totalPages = 1): Page<AdminDoctor> => ({
  content: items, page: p, size: 10, totalElements: items.length, totalPages, hasNext: p + 1 < totalPages,
});

describe('AdminDoctorsComponent', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<AdminDoctorsComponent>;
  let root: HTMLElement;

  const listRequests = () => http.match((r) => r.url === LIST && r.method === 'GET');
  const buttons = (label: string) => [...root.querySelectorAll<HTMLButtonElement>('button')].filter((b) => b.textContent?.trim() === label);

  function start(first: Page<AdminDoctor> = page([doctor(1), doctor(2, { active: false }), doctor(3, { setupPending: true })])) {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: API_URL, useValue: API }],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AdminDoctorsComponent);
    root = fixture.nativeElement;
    fixture.detectChanges();
    listRequests()[0].flush(first);
    fixture.detectChanges();
  }
  afterEach(() => http.verify());

  it('lists doctors with their status badges', () => {
    start();
    const text = root.textContent!;
    expect(text).toContain('Dr. Number 1');
    expect(root.querySelectorAll('article').length).toBe(3);
    expect(text).toContain('Deactivated');
    expect(text).toContain('Invite pending');
  });

  it('only offers "Resend invite" to active doctors who have not finished setup', () => {
    start();
    expect(buttons('Resend invite').length).toBe(1);
  });

  it('the status chips filter on the server', () => {
    start();
    buttons('Deactivated').find((b) => b.getAttribute('role') === 'tab')!.click();
    const req = listRequests()[0];
    expect(req.request.params.get('active')).toBe('false');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(page([doctor(2, { active: false })]));
  });

  it('searching waits for a pause in typing, then asks the server', fakeAsync(() => {
    start();
    const input = root.querySelector<HTMLInputElement>('input[type=search]')!;
    for (const value of ['v', 've', 'verma']) {
      input.value = value;
      input.dispatchEvent(new Event('input'));
      tick(100);
    }
    expect(listRequests().length).toBe(0); // still typing: nothing sent
    tick(300);
    const reqs = listRequests();
    expect(reqs.length).toBe(1);
    expect(reqs[0].request.params.get('q')).toBe('verma');
    reqs[0].flush(page([]));
  }));

  it('deactivating asks for confirmation first, then PATCHes and refreshes', () => {
    start();
    buttons('Deactivate')[0].click();
    fixture.detectChanges();
    expect(root.textContent).toContain('Deactivate this doctor?');
    http.expectNone(`${LIST}/1`);

    buttons('Deactivate').find((b) => b.classList.contains('danger') && root.querySelector('app-confirm-dialog')?.contains(b))!.click();
    const patch = http.expectOne(`${LIST}/1`);
    expect(patch.request.method).toBe('PATCH');
    expect(patch.request.body).toEqual({ active: false });
    patch.flush(doctor(1, { active: false }));
    listRequests()[0].flush(page([doctor(1, { active: false })]));
  });

  it('reactivating needs no confirmation', () => {
    start();
    buttons('Reactivate')[0].click();
    const patch = http.expectOne(`${LIST}/2`);
    expect(patch.request.body).toEqual({ active: true });
    patch.flush(doctor(2));
    listRequests()[0].flush(page([doctor(2)]));
  });

  it('shows an error with a retry when loading fails', () => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: API_URL, useValue: API }],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AdminDoctorsComponent);
    root = fixture.nativeElement;
    fixture.detectChanges();
    listRequests()[0].flush({ message: 'Boom' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();

    expect(root.textContent).toContain('Boom');
    buttons('Retry')[0].click();
    listRequests()[0].flush(page([]));
  });

  it('opens the create dialog from "Add doctor"', () => {
    start();
    buttons('Add doctor')[0].click();
    fixture.detectChanges();
    expect(root.querySelector('app-doctor-dialog')).not.toBeNull();
    expect(root.textContent).toContain('Send invitation');
  });
});
