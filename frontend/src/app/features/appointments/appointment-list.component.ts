import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Appointment } from '../../core/models/models';
import { AppointmentStore, StatusFilter } from '../../core/services/appointment.store';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { parseApiError } from '../../core/utils/error.util';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { RescheduleDialogComponent } from './reschedule-dialog.component';

@Component({
  selector: 'app-appointment-list',
  imports: [DatePipe, RouterLink, StatusBadgeComponent, ConfirmDialogComponent, RescheduleDialogComponent],
  templateUrl: './appointment-list.component.html',
  styleUrl: './appointment-list.component.scss',
})
export class AppointmentListComponent {
  protected readonly store = inject(AppointmentStore);
  protected readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  protected readonly filters: { value: StatusFilter; label: string }[] = [
    { value: 'ALL', label: 'All' },
    { value: 'BOOKED', label: 'Upcoming' },
    { value: 'COMPLETED', label: 'Completed' },
    { value: 'CANCELLED', label: 'Cancelled' },
  ];

  /** The appointment the user is about to cancel (drives the confirm dialog). */
  protected readonly pendingCancel = signal<Appointment | null>(null);
  /** The appointment being rescheduled (drives the reschedule dialog). */
  protected readonly rescheduling = signal<Appointment | null>(null);

  constructor() {
    this.store.loadPage(0); // always start from the first page when opening the list
  }

  protected retry(): void {
    this.store.loadPage();
  }

  protected confirmCancel(): void {
    const appt = this.pendingCancel();
    if (!appt) return;
    this.pendingCancel.set(null);
    this.store.cancel(appt.id).subscribe({
      next: () => this.toast.success('Appointment cancelled.'),
      error: (err) => this.toast.error(parseApiError(err).message),
    });
  }

  protected complete(appt: Appointment): void {
    this.store.complete(appt.id).subscribe({
      next: () => this.toast.success('Marked as completed.'),
      error: (err) => this.toast.error(parseApiError(err).message),
    });
  }
}
