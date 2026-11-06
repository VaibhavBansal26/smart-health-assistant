import { DatePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { AppointmentStore } from '../../core/services/appointment.store';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { firstName } from '../../shared/utils/name.util';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, DatePipe, StatusBadgeComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  protected readonly auth = inject(AuthService);
  protected readonly store = inject(AppointmentStore);

  protected readonly firstName = computed(() => firstName(this.auth.user()?.fullName));

  // Each tile is derived from store.counts(): no manual syncing needed.
  protected readonly tiles = computed(() => {
    const c = this.store.counts();
    return [
      { label: 'Total', value: c.total, tone: 'neutral' },
      { label: 'Upcoming', value: c.booked, tone: 'info' },
      { label: 'Completed', value: c.completed, tone: 'ok' },
      { label: 'Cancelled', value: c.cancelled, tone: 'danger' },
    ];
  });

  private readonly router = inject(Router);

  constructor() {
    if (this.auth.role() === 'ADMIN') {
      this.router.navigateByUrl('/admin/doctors', { replaceUrl: true }); // admins have no appointments
      return;
    }
    this.store.loadSummary();
  }

  protected retry(): void {
    this.store.loadSummary();
  }
}
