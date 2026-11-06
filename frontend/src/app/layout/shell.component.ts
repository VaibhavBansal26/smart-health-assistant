import { TitleCasePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/services/auth.service';
import { ToastService } from '../core/services/toast.service';
import { parseApiError } from '../core/utils/error.util';
import { InitialsPipe } from '../shared/pipes/initials.pipe';

/** Layout for all logged-in pages: top bar + the active child route below it. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, InitialsPipe, TitleCasePipe],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  protected readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  protected readonly resending = signal(false);
  protected readonly resent = signal(false);

  /** Asks the server to send another verification email (it throttles to one a minute). */
  protected resend(): void {
    this.resending.set(true);
    this.auth.resendVerification().subscribe({
      next: () => {
        this.resent.set(true);
        this.resending.set(false);
        this.toast.success('Verification email sent. Check your inbox.');
      },
      error: (err) => {
        this.resending.set(false);
        this.toast.error(parseApiError(err).message);
      },
    });
  }
}
