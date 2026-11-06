import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { API_URL } from '../../core/api.config';
import { ForgotPasswordComponent } from './forgot-password.component';
import { ResetPasswordComponent } from './reset-password.component';
import { VerifyEmailComponent } from './verify-email.component';

const API = 'http://api.test';
const noContent = { status: 204, statusText: 'No Content' };

function configure(): { http: HttpTestingController; navigate: jasmine.Spy } {
  localStorage.clear();
  TestBed.configureTestingModule({
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: API_URL, useValue: API }],
  });
  return {
    http: TestBed.inject(HttpTestingController),
    navigate: spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true),
  };
}

function type(root: HTMLElement, selector: string, value: string): void {
  const el = root.querySelector<HTMLInputElement>(selector)!;
  el.value = value;
  el.dispatchEvent(new Event('input'));
}

const text = (f: ComponentFixture<unknown>) => (f.nativeElement as HTMLElement).textContent ?? '';

describe('VerifyEmailComponent', () => {
  it('redeems the token, shows success, and removes the token from the address bar', async () => {
    const { http, navigate } = configure();
    const fixture = TestBed.createComponent(VerifyEmailComponent);
    fixture.componentRef.setInput('token', 'abc123');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith([], jasmine.objectContaining({ queryParams: { token: null }, replaceUrl: true }));
    const req = http.expectOne(`${API}/auth/verify-email`);
    expect(req.request.body).toEqual({ token: 'abc123' });
    expect(text(fixture)).toContain('Verifying');

    req.flush(null, noContent);
    fixture.detectChanges();
    expect(text(fixture)).toContain('Email verified');
    http.verify();
  });

  it('explains a rejected link', async () => {
    const { http } = configure();
    const fixture = TestBed.createComponent(VerifyEmailComponent);
    fixture.componentRef.setInput('token', 'used-already');
    fixture.detectChanges();
    await fixture.whenStable();

    http.expectOne(`${API}/auth/verify-email`).flush({ message: 'This link is invalid or has expired.' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(text(fixture)).toContain('could not verify');
    expect(text(fixture)).toContain('invalid or has expired');
    http.verify();
  });

  it('fails without a request when the link has no token', async () => {
    const { http } = configure();
    const fixture = TestBed.createComponent(VerifyEmailComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    http.expectNone(`${API}/auth/verify-email`);
    expect(text(fixture)).toContain('missing its token');
  });
});

describe('ResetPasswordComponent', () => {
  it('without a token it offers to request a new link instead of a form', () => {
    configure();
    const fixture = TestBed.createComponent(ResetPasswordComponent);
    fixture.detectChanges();
    expect(text(fixture)).toContain('cannot be used');
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
    expect(fixture.nativeElement.querySelector('a[href="/forgot-password"]')).not.toBeNull();
  });

  it('validates locally: weak or mismatching passwords never reach the server', () => {
    const { http } = configure();
    const fixture = TestBed.createComponent(ResetPasswordComponent);
    fixture.componentRef.setInput('token', 'tok');
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;

    type(root, '#password', 'abcdefgh'); // no number
    type(root, '#confirmPassword', 'abcdefgh');
    root.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    http.expectNone(`${API}/auth/reset-password`);
    expect(text(fixture)).toContain('one letter and one number');

    type(root, '#password', 'Good-Pass-1');
    type(root, '#confirmPassword', 'Different-1');
    root.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    http.expectNone(`${API}/auth/reset-password`);
    expect(text(fixture)).toContain('do not match');
  });

  it('sends the token and new password, then goes to the login page without the token in history', () => {
    const { http, navigate } = configure();
    const fixture = TestBed.createComponent(ResetPasswordComponent);
    fixture.componentRef.setInput('token', 'tok-xyz');
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;

    type(root, '#password', 'Good-Pass-1');
    type(root, '#confirmPassword', 'Good-Pass-1');
    root.querySelector('form')!.dispatchEvent(new Event('submit'));

    const req = http.expectOne(`${API}/auth/reset-password`);
    expect(req.request.body).toEqual({ token: 'tok-xyz', newPassword: 'Good-Pass-1' });
    req.flush(null, noContent);
    expect(navigate).toHaveBeenCalledWith(['/login'], { replaceUrl: true });
  });

  it('a rejected (expired/used) link switches to the "request a new link" view', () => {
    const { http } = configure();
    const fixture = TestBed.createComponent(ResetPasswordComponent);
    fixture.componentRef.setInput('token', 'old');
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    type(root, '#password', 'Good-Pass-1');
    type(root, '#confirmPassword', 'Good-Pass-1');
    root.querySelector('form')!.dispatchEvent(new Event('submit'));

    http.expectOne(`${API}/auth/reset-password`).flush({ message: 'This link is invalid or has expired.' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(text(fixture)).toContain('cannot be used');
    expect(text(fixture)).toContain('Request a new link');
  });
});

describe('ForgotPasswordComponent', () => {
  it('does not call the server for an invalid email', () => {
    const { http } = configure();
    const fixture = TestBed.createComponent(ForgotPasswordComponent);
    fixture.detectChanges();
    type(fixture.nativeElement, '#email', 'not-an-email');
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    http.expectNone(`${API}/auth/forgot-password`);
    expect(text(fixture)).toContain('valid email address'); // the user is told why nothing happened
  });

  it('shows the same neutral confirmation the server gives, which never says whether the account exists', () => {
    const { http } = configure();
    const fixture = TestBed.createComponent(ForgotPasswordComponent);
    fixture.detectChanges();
    type(fixture.nativeElement, '#email', ' maya@test.dev ');
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    const req = http.expectOne(`${API}/auth/forgot-password`);
    expect(req.request.body).toEqual({ email: 'maya@test.dev' });
    req.flush({ message: 'If an account exists for that email, we have sent a link to reset the password.' });
    fixture.detectChanges();
    expect(text(fixture)).toContain('If an account exists');
  });

  it('shows throttling errors', () => {
    const { http } = configure();
    const fixture = TestBed.createComponent(ForgotPasswordComponent);
    fixture.detectChanges();
    type(fixture.nativeElement, '#email', 'maya@test.dev');
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    http.expectOne(`${API}/auth/forgot-password`).flush({ message: 'Too many requests from your network. Please try again later.' }, { status: 429, statusText: 'Too Many' });
    fixture.detectChanges();
    expect(text(fixture)).toContain('Too many requests');
  });
});
