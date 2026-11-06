import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Appointment, UpdateAppointmentPayload } from '../../core/models/models';
import { AppointmentStore } from '../../core/services/appointment.store';
import { ToastService } from '../../core/services/toast.service';
import { parseApiError } from '../../core/utils/error.util';
import { FieldErrorComponent } from '../../shared/components/field-error.component';
import { SpinnerComponent } from '../../shared/components/spinner.component';
import { AutofocusDirective } from '../../shared/directives/autofocus.directive';
import { futureDateTime } from '../../shared/validators/validators';

/** Modal to move an appointment and/or edit its reason. Sends a PATCH with only the fields that changed. */
@Component({
  selector: 'app-reschedule-dialog',
  imports: [ReactiveFormsModule, DatePipe, FieldErrorComponent, SpinnerComponent, AutofocusDirective],
  templateUrl: './reschedule-dialog.component.html',
  styleUrl: './reschedule-dialog.component.scss',
})
export class RescheduleDialogComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly store = inject(AppointmentStore);
  private readonly toast = inject(ToastService);

  readonly appointment = input.required<Appointment>();
  readonly saved = output<void>();
  readonly dismissed = output<void>();

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  /** yyyy-MM-ddTHH:mm, what <input type="datetime-local"> expects, in local time. */
  protected readonly minDateTime = RescheduleDialogComponent.toLocalInput(new Date());

  protected readonly form = this.fb.group({
    dateTime: ['', [Validators.required, futureDateTime]],
    reason: ['', [Validators.required, Validators.maxLength(500)]],
  });

  /** Fields that differ from the saved appointment: only these are sent. */
  private readonly changes = computed<UpdateAppointmentPayload>(() => {
    const a = this.appointment();
    const v = this.currentValue();
    const payload: UpdateAppointmentPayload = {};
    if (v.dateTime && v.dateTime !== a.dateTime.slice(0, 16)) payload.dateTime = v.dateTime;
    if (v.reason.trim() && v.reason.trim() !== a.reason) payload.reason = v.reason.trim();
    return payload;
  });
  private readonly currentValue = signal({ dateTime: '', reason: '' });

  constructor() {
    queueMicrotask(() => {
      const a = this.appointment();
      this.form.setValue({ dateTime: a.dateTime.slice(0, 16), reason: a.reason });
      this.currentValue.set(this.form.getRawValue());
    });
    this.form.valueChanges.subscribe(() => this.currentValue.set(this.form.getRawValue()));
  }

  protected get hasChanges(): boolean {
    return Object.keys(this.changes()).length > 0;
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (!this.hasChanges) return;

    this.submitting.set(true);
    this.error.set(null);
    this.store.update(this.appointment().id, this.changes()).subscribe({
      next: () => {
        this.toast.success('Appointment updated.');
        this.saved.emit();
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

  private static toLocalInput(d: Date): string {
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }
}
