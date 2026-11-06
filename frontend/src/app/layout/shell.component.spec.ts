import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Role, User } from '../core/models/models';
import { AuthService } from '../core/services/auth.service';
import { ToastService } from '../core/services/toast.service';
import { ShellComponent } from './shell.component';

const make = (role: Role, emailVerified: boolean): User => ({
  id: 1, fullName: 'Maya Patel', email: 'maya@test.dev', role, specialty: null, emailVerified, active: true,
});

describe('ShellComponent', () => {
  function render(user: User, resend = () => of(undefined as void)) {
    const resendVerification = jasmine.createSpy('resendVerification').and.callFake(resend);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { user: signal(user), role: signal(user.role), logout: () => undefined, resendVerification } },
        { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['success', 'error']) },
      ],
    });
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    return { fixture, root: fixture.nativeElement as HTMLElement, resendVerification };
  }

  it('warns unverified patients, naming the address the link went to', () => {
    const { root } = render(make('PATIENT', false));
    const banner = root.querySelector('.verify-banner');
    expect(banner?.textContent).toContain('Verify your email');
    expect(banner?.textContent).toContain('maya@test.dev');
  });

  it('shows no banner to verified patients, or to doctors and admins', () => {
    for (const user of [make('PATIENT', true), make('DOCTOR', false), make('ADMIN', false)]) {
      TestBed.resetTestingModule();
      expect(render(user).root.querySelector('.verify-banner')).withContext(user.role).toBeNull();
    }
  });

  it('"Resend email" asks the server once and then confirms', () => {
    const { fixture, root, resendVerification } = render(make('PATIENT', false));
    const button = root.querySelector<HTMLButtonElement>('.verify-banner button')!;
    button.click();
    fixture.detectChanges();

    expect(resendVerification).toHaveBeenCalledTimes(1);
    expect(button.textContent).toContain('Email sent');
    expect(button.disabled).toBeTrue();
  });

  it('a throttled resend reports the server message and can be retried', () => {
    const toast = { error: jasmine.createSpy('error'), success: jasmine.createSpy('success') };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            user: signal(make('PATIENT', false)), role: signal('PATIENT'), logout: () => undefined,
            resendVerification: () => throwError(() => new HttpErrorResponse({ status: 429, error: { message: 'A link was just sent.' } })),
          },
        },
        { provide: ToastService, useValue: toast },
      ],
    });
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('.verify-banner button') as HTMLButtonElement;
    button.click();
    fixture.detectChanges();

    expect(toast.error).toHaveBeenCalledWith('A link was just sent.');
    expect(button.disabled).toBeFalse();
  });

  it('admins see only the Doctors link; everyone else sees Dashboard and Appointments', () => {
    const adminNav = render(make('ADMIN', true)).root.querySelector('nav')!.textContent!;
    expect(adminNav).toContain('Doctors');
    expect(adminNav).not.toContain('Appointments');

    TestBed.resetTestingModule();
    const patientNav = render(make('PATIENT', true)).root.querySelector('nav')!.textContent!;
    expect(patientNav).toContain('Appointments');
    expect(patientNav).not.toContain('Doctors');
  });
});
