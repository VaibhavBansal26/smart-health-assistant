import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, startWith } from 'rxjs';
import { User } from '../../core/models/models';
import { AppointmentStore } from '../../core/services/appointment.store';
import { BookingDraftService } from '../../core/services/booking-draft.service';
import { DoctorService } from '../../core/services/doctor.service';
import { ToastService } from '../../core/services/toast.service';
import { parseApiError } from '../../core/utils/error.util';
import { FieldErrorComponent } from '../../shared/components/field-error.component';
import { SpinnerComponent } from '../../shared/components/spinner.component';
import { InitialsPipe } from '../../shared/pipes/initials.pipe';
import { futureDateTime } from '../../shared/validators/validators';

type LoadState = 'loading' | 'loaded' | 'error';

@Component({
  selector: 'app-appointment-form',
  imports: [ReactiveFormsModule, RouterLink, FieldErrorComponent, SpinnerComponent, InitialsPipe],
  templateUrl: './appointment-form.component.html',
  styleUrl: './appointment-form.component.scss',
})
export class AppointmentFormComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly doctorsApi = inject(DoctorService);
  private readonly store = inject(AppointmentStore);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  // ---- doctors list (own loading/error state) ----
  protected readonly doctors = signal<User[]>([]);
  protected readonly doctorsState = signal<LoadState>('loading');

  // ---- search box: RxJS stream -> signal ----
  protected readonly search = new FormControl('', { nonNullable: true });
  private readonly term = toSignal(
    this.search.valueChanges.pipe(debounceTime(250), distinctUntilChanged(), startWith('')),
    { initialValue: '' },
  );

  /** Re-computed automatically when `doctors` or the search term change. */
  protected readonly visibleDoctors = computed(() => {
    const t = this.term().trim().toLowerCase();
    return t
      ? this.doctors().filter(
          (d) => d.fullName.toLowerCase().includes(t) || (d.specialty ?? '').toLowerCase().includes(t),
        )
      : this.doctors();
  });

  // ---- the booking form ----
  protected readonly form = this.fb.group({
    doctorId: this.fb.control<number | null>(null, Validators.required),
    dateTime: ['', [Validators.required, futureDateTime]],
    reason: ['', [Validators.required, Validators.maxLength(500)]],
  });

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Earliest value the date picker allows, formatted as yyyy-MM-ddTHH:mm in local time. */
  protected readonly minDateTime = this.toLocalInput(new Date());

  /** Set when arriving from "Find a doctor" (see BookingDraftService). Single use. */
  private readonly draft = inject(BookingDraftService).take();

  constructor() {
    if (this.draft) {
      this.form.controls.reason.setValue(this.draft.reason);
    }
    this.loadDoctors();
  }

  protected loadDoctors(): void {
    this.doctorsState.set('loading');
    this.doctorsApi.list().subscribe({
      next: (list) => {
        this.doctors.set(list);
        this.doctorsState.set('loaded');
        // preselect the recommended doctor if they still exist
        const wanted = list.find((d) => d.id === this.draft?.doctorId);
        if (wanted && this.form.controls.doctorId.value === null) {
          this.form.controls.doctorId.setValue(wanted.id);
        }
      },
      error: () => this.doctorsState.set('error'),
    });
  }

  protected selectDoctor(d: User): void {
    this.form.controls.doctorId.setValue(d.id);
    this.form.controls.doctorId.markAsTouched();
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { doctorId, dateTime, reason } = this.form.getRawValue();
    this.submitting.set(true);
    this.error.set(null);

    this.store.create({ doctorId: doctorId!, dateTime, reason }).subscribe({
      next: () => {
        this.toast.success('Appointment booked!');
        this.router.navigateByUrl('/appointments');
      },
      error: (err) => {
        const e = parseApiError(err);
        for (const [field, msg] of Object.entries(e.fieldErrors)) {
          this.form.get(field)?.setErrors({ server: msg });
        }
        this.error.set(e.message); // e.g. 409 "That time slot is already booked"
        this.submitting.set(false);
      },
    });
  }

  private toLocalInput(d: Date): string {
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }
}
