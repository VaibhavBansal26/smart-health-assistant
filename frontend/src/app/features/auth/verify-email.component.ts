import { Component, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { parseApiError } from '../../core/utils/error.util';
import { SpinnerComponent } from '../../shared/components/spinner.component';

type State = 'verifying' | 'verified' | 'failed';

/** Landing page for the link in the verification email. */
@Component({
  selector: 'app-verify-email',
  imports: [RouterLink, SpinnerComponent],
  templateUrl: './verify-email.component.html',
  styleUrl: './auth-layout.scss',
})
export class VerifyEmailComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly token = input<string>();

  protected readonly state = signal<State>('verifying');
  protected readonly error = signal<string | null>(null);
  protected readonly signedIn = this.auth.isLoggedIn;

  constructor() {
    // inputs are set after construction, so wait one microtask before reading the token
    queueMicrotask(() => this.verify());
  }

  private verify(): void {
    const token = this.token();
    // take the one-time token out of the address bar and history right away
    this.router.navigate([], { queryParams: { token: null }, queryParamsHandling: 'merge', replaceUrl: true });

    if (!token) {
      this.state.set('failed');
      this.error.set('This link is missing its token.');
      return;
    }
    this.auth.verifyEmail(token).subscribe({
      next: () => this.state.set('verified'),
      error: (err) => {
        this.error.set(parseApiError(err).message);
        this.state.set('failed');
      },
    });
  }
}
