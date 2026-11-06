import { TitleCasePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { AppointmentStatus } from '../../core/models/models';

/** Reusable presentational component: data in via input(), nothing out. */
@Component({
  selector: 'app-status-badge',
  imports: [TitleCasePipe],
  template: `<span class="badge" [class]="'badge ' + status()">{{ status() | titlecase }}</span>`,
  styles: `
    .badge.BOOKED { background: var(--info-soft); color: var(--info); }
    .badge.COMPLETED { background: var(--ok-soft); color: var(--ok); }
    .badge.CANCELLED { background: var(--danger-soft); color: #991b1b; }
  `,
})
export class StatusBadgeComponent {
  readonly status = input.required<AppointmentStatus>();
}
