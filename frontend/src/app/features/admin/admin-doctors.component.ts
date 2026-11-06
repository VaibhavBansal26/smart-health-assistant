import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { AdminDoctor, Page } from '../../core/models/models';
import { AdminDoctorService } from '../../core/services/admin-doctor.service';
import { ToastService } from '../../core/services/toast.service';
import { parseApiError } from '../../core/utils/error.util';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog.component';
import { DoctorDialogComponent } from './doctor-dialog.component';

type Status = 'loading' | 'loaded' | 'error';
type StatusFilter = 'ALL' | 'ACTIVE' | 'INACTIVE';

/** Administrator page: create doctor accounts, edit them, and deactivate or reactivate them. */
@Component({
  selector: 'app-admin-doctors',
  imports: [ReactiveFormsModule, ConfirmDialogComponent, DoctorDialogComponent],
  templateUrl: './admin-doctors.component.html',
  styleUrl: './admin-doctors.component.scss',
})
export class AdminDoctorsComponent {
  private readonly api = inject(AdminDoctorService);
  private readonly toast = inject(ToastService);

  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly statusFilter = signal<StatusFilter>('ALL');
  protected readonly filters: { value: StatusFilter; label: string }[] = [
    { value: 'ALL', label: 'All' },
    { value: 'ACTIVE', label: 'Active' },
    { value: 'INACTIVE', label: 'Deactivated' },
  ];

  protected readonly status = signal<Status>('loading');
  protected readonly error = signal<string | null>(null);
  protected readonly page = signal<Page<AdminDoctor> | null>(null);

  protected readonly dialogOpen = signal(false);
  protected readonly editing = signal<AdminDoctor | null>(null);
  protected readonly pendingToggle = signal<AdminDoctor | null>(null);
  protected readonly busyId = signal<number | null>(null);

  /** Guards against slow, out-of-order responses while typing in the search box. */
  private requestId = 0;

  constructor() {
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(() => this.load(0));
    this.load(0);
  }

  protected load(pageIndex: number, silent = false): void {
    const request = ++this.requestId;
    if (!silent) this.status.set('loading');
    const f = this.statusFilter();
    this.api.list({ q: this.search.value, active: f === 'ALL' ? null : f === 'ACTIVE', page: pageIndex }).subscribe({
      next: (p) => {
        if (request !== this.requestId) return;
        if (p.content.length === 0 && p.page > 0 && p.totalPages > 0) {
          this.load(p.totalPages - 1, silent); // the last item of the last page moved away
          return;
        }
        this.page.set(p);
        this.status.set('loaded');
      },
      error: (err) => {
        if (request !== this.requestId) return;
        this.error.set(parseApiError(err).message);
        this.status.set('error');
      },
    });
  }

  protected setFilter(f: StatusFilter): void {
    if (f === this.statusFilter()) return;
    this.statusFilter.set(f);
    this.load(0);
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.dialogOpen.set(true);
  }

  protected openEdit(d: AdminDoctor): void {
    this.editing.set(d);
    this.dialogOpen.set(true);
  }

  protected onSaved(): void {
    this.dialogOpen.set(false);
    this.load(this.page()?.page ?? 0, true);
  }

  /** Deactivating asks first (it signs the doctor out and hides them); reactivating needs no confirmation. */
  protected toggle(d: AdminDoctor): void {
    if (d.active) {
      this.pendingToggle.set(d);
    } else {
      this.setActive(d, true);
    }
  }

  protected confirmDeactivate(): void {
    const d = this.pendingToggle();
    this.pendingToggle.set(null);
    if (d) this.setActive(d, false);
  }

  protected resendInvite(d: AdminDoctor): void {
    this.busyId.set(d.id);
    this.api.resendInvite(d.id).subscribe({
      next: () => {
        this.busyId.set(null);
        this.toast.success(`New invitation sent to ${d.email}.`);
      },
      error: (err) => {
        this.busyId.set(null);
        this.toast.error(parseApiError(err).message);
      },
    });
  }

  private setActive(d: AdminDoctor, active: boolean): void {
    this.busyId.set(d.id);
    this.api.update(d.id, { active }).subscribe({
      next: () => {
        this.busyId.set(null);
        this.toast.success(active ? `${d.fullName} is active again.` : `${d.fullName} was deactivated and signed out.`);
        this.load(this.page()?.page ?? 0, true);
      },
      error: (err) => {
        this.busyId.set(null);
        this.toast.error(parseApiError(err).message);
      },
    });
  }
}
