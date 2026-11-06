import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { parseApiError } from '../../core/utils/error.util';
import { FieldErrorComponent } from '../../shared/components/field-error.component';
import { SpinnerComponent } from '../../shared/components/spinner.component';
import { firstName } from '../../shared/utils/name.util';
import { fieldsMatch, passwordStrength } from '../../shared/validators/validators';

/** Patients register themselves. Doctors are created by an administrator, who emails them an invitation. */
@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, FieldErrorComponent, SpinnerComponent],
  templateUrl: './register.component.html',
  styleUrl: './auth-layout.scss',
})
export class RegisterComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly form = this.fb.group(
    {
      fullName: ['', [Validators.required, Validators.maxLength(100)]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8), passwordStrength]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: fieldsMatch('password', 'confirmPassword') },
  );

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);

    const { fullName, email, password } = this.form.getRawValue();
    this.auth.register({ fullName, email, password }).subscribe({
      next: (user) => {
        this.toast.success(`Welcome, ${firstName(user.fullName)}! Check your inbox to verify your email.`);
        this.router.navigateByUrl('/dashboard');
      },
      error: (err) => {
        const e = parseApiError(err);
        // Server-side field errors (e.g. email already used) shown next to the field
        for (const [field, msg] of Object.entries(e.fieldErrors)) {
          this.form.get(field)?.setErrors({ server: msg });
        }
        if (e.status === 409) {
          this.form.controls.email.setErrors({ server: e.message });
          this.form.controls.email.markAsTouched();
        }
        this.error.set(e.message);
        this.submitting.set(false);
      },
    });
  }
}
