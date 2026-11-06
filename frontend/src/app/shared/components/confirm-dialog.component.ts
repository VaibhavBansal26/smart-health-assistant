import { Component, input, output } from '@angular/core';

/**
 * Reusable modal. Parent controls visibility with @if and listens to outputs:
 *   <app-confirm-dialog title="..." message="..." (confirmed)="..." (dismissed)="..." />
 */
@Component({
  selector: 'app-confirm-dialog',
  template: `
    <div class="backdrop" (click)="dismissed.emit()">
      <div class="dialog card" role="dialog" aria-modal="true" [attr.aria-label]="title()"
           (click)="$event.stopPropagation()" (keydown.escape)="dismissed.emit()">
        <h2>{{ title() }}</h2>
        <p class="muted">{{ message() }}</p>
        <div class="row actions">
          <button type="button" class="btn secondary" (click)="dismissed.emit()">Keep it</button>
          <button type="button" class="btn danger" (click)="confirmed.emit()">{{ confirmLabel() }}</button>
        </div>
      </div>
    </div>
  `,
  styles: `
    .backdrop { position: fixed; inset: 0; background: rgb(15 23 42 / 45%); display: grid;
      place-items: center; padding: 16px; z-index: 900; }
    .dialog { width: min(420px, 100%); display: flex; flex-direction: column; gap: 12px; }
    .actions { justify-content: flex-end; margin-top: 8px; }
  `,
})
export class ConfirmDialogComponent {
  readonly title = input.required<string>();
  readonly message = input.required<string>();
  readonly confirmLabel = input('Confirm');

  readonly confirmed = output<void>();
  readonly dismissed = output<void>();
}
