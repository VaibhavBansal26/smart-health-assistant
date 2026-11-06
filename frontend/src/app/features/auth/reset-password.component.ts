import { Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { parseApiError } from '../../core/utils/error.util';
import { FieldErrorComponent } from '../../shared/components/field-error.component';
import { SpinnerComponent } from '../../shared/components/spinner.component';
import { fieldsMatch, passwordStrength } from '../../shared/validators/validators';

/** Landing page for the link in the "reset your password" and "set up your doctor account" emails. */
@Component({
  selector: 'app-reset-password',
  imports: [ReactiveFormsModule, RouterLink, FieldErrorComponent, SpinnerComponent],
  templateUrl: './reset-password.component.html',
  styleUrl: './auth-layout.scss',
})
export class ResetPasswordComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  /** From "?token=..." (route input binding). */
  readonly token = input<string>();

  protected readonly form = this.fb.group(
    {
      password: ['', [Validators.required, Validators.minLength(8), passwordStrength]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: fieldsMatch('password', 'confirmPassword') },
  );

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  /** The server said the link is bad/expired/used: offer to request a new one. */
  protected readonly linkRejected = signal(false);

  protected submit(): void {
    const token = this.token();
    if (!token || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.auth.resetPassword(token, this.form.controls.password.value).subscribe({
      next: () => {
        this.toast.success('Password updated. Please sign in.');
        this.router.navigate(['/login'], { replaceUrl: true }); // replaceUrl: drop the token from history
      },
      error: (err) => {
        const e = parseApiError(err);
        this.error.set(e.message);
        this.linkRejected.set(e.status === 400 && !e.fieldErrors['newPassword']);
        this.submitting.set(false);
      },
    });
  }
}
