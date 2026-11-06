import { Component, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AdminDoctor } from '../../core/models/models';
import { AdminDoctorService } from '../../core/services/admin-doctor.service';
import { ToastService } from '../../core/services/toast.service';
import { parseApiError } from '../../core/utils/error.util';
import { FieldErrorComponent } from '../../shared/components/field-error.component';
import { SpinnerComponent } from '../../shared/components/spinner.component';
import { AutofocusDirective } from '../../shared/directives/autofocus.directive';

/** Create a doctor (no `doctor` input) or edit one. The email cannot be changed after creation. */
@Component({
  selector: 'app-doctor-dialog',
  imports: [ReactiveFormsModule, FieldErrorComponent, SpinnerComponent, AutofocusDirective],
  templateUrl: './doctor-dialog.component.html',
  styleUrl: './doctor-dialog.component.scss',
})
export class DoctorDialogComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly api = inject(AdminDoctorService);
  private readonly toast = inject(ToastService);

  readonly doctor = input<AdminDoctor | null>(null);
  readonly saved = output<void>();
  readonly dismissed = output<void>();

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.group({
    fullName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email]],
    specialty: ['', [Validators.required, Validators.maxLength(100)]],
  });

  constructor() {
    queueMicrotask(() => {
      const d = this.doctor();
      if (d) {
        this.form.setValue({ fullName: d.fullName, email: d.email, specialty: d.specialty });
        this.form.controls.email.disable(); // cannot be changed once the account exists
      }
    });
  }

  protected get editing(): boolean {
    return this.doctor() !== null;
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { fullName, email, specialty } = this.form.getRawValue();
    this.submitting.set(true);
    this.error.set(null);

    const d = this.doctor();
    const request = d
      ? this.api.update(d.id, { fullName: fullName.trim(), specialty: specialty.trim() })
      : this.api.create({ fullName: fullName.trim(), email: email.trim(), specialty: specialty.trim() });

    request.subscribe({
      next: () => {
        this.toast.success(d ? 'Doctor updated.' : `Invitation sent to ${email.trim()}.`);
        this.saved.emit();
      },
      error: (err) => {
        const e = parseApiError(err);
        for (const [field, msg] of Object.entries(e.fieldErrors)) {
          this.form.get(field)?.setErrors({ server: msg });
        }
        this.error.set(e.message);
        this.submitting.set(false);
      },
    });
  }
}
