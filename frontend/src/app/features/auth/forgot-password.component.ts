import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { parseApiError } from '../../core/utils/error.util';
import { FieldErrorComponent } from '../../shared/components/field-error.component';
import { SpinnerComponent } from '../../shared/components/spinner.component';
import { AutofocusDirective } from '../../shared/directives/autofocus.directive';

@Component({
  selector: 'app-forgot-password',
  imports: [ReactiveFormsModule, RouterLink, FieldErrorComponent, SpinnerComponent, AutofocusDirective],
  templateUrl: './forgot-password.component.html',
  styleUrl: './auth-layout.scss',
})
export class ForgotPasswordComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);

  protected readonly form = this.fb.group({ email: ['', [Validators.required, Validators.email]] });
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  /** The server's (deliberately vague) confirmation, shown whether or not the account exists. */
  protected readonly confirmation = signal<string | null>(null);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.auth.forgotPassword(this.form.controls.email.value.trim()).subscribe({
      next: (res) => {
        this.confirmation.set(res.message);
        this.submitting.set(false);
      },
      error: (err) => {
        this.error.set(parseApiError(err).message);
        this.submitting.set(false);
      },
    });
  }
}
