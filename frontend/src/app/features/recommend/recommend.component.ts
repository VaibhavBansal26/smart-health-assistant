import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { DoctorMatch, RecommendResponse } from '../../core/models/models';
import { BookingDraftService } from '../../core/services/booking-draft.service';
import { RecommendationService } from '../../core/services/recommendation.service';
import { SymptomDraftService } from '../../core/services/symptom-draft.service';
import { parseApiError } from '../../core/utils/error.util';
import { FieldErrorComponent } from '../../shared/components/field-error.component';
import { SpinnerComponent } from '../../shared/components/spinner.component';
import { InitialsPipe } from '../../shared/pipes/initials.pipe';

@Component({
  selector: 'app-recommend',
  imports: [ReactiveFormsModule, DecimalPipe, FieldErrorComponent, SpinnerComponent, InitialsPipe],
  templateUrl: './recommend.component.html',
  styleUrl: './recommend.component.scss',
})
export class RecommendComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly api = inject(RecommendationService);
  private readonly drafts = inject(BookingDraftService);
  private readonly router = inject(Router);

  protected readonly examples = [
    'I have an itchy red rash on my arms and my skin is very dry',
    'My heart races when I climb stairs and I feel out of breath',
    'I have had a burning pain in my stomach after meals for a week',
  ];

  protected readonly form = this.fb.group({
    symptoms: ['', [Validators.required, Validators.minLength(5), Validators.maxLength(1000)]],
  });

  constructor() {
    // arriving from the chat: prefill what the user already told Aria (they still press the button)
    const fromChat = inject(SymptomDraftService).take();
    if (fromChat) {
      this.form.controls.symptoms.setValue(fromChat);
    }
  }

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly result = signal<RecommendResponse | null>(null);
  /** The text the current result was computed for (the textarea may change afterwards). */
  private readonly askedAbout = signal('');

  protected useExample(text: string): void {
    this.form.controls.symptoms.setValue(text);
    this.form.controls.symptoms.markAsDirty();
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const symptoms = this.form.controls.symptoms.value.trim();
    this.loading.set(true);
    this.error.set(null);
    this.result.set(null);

    this.api.recommend(symptoms).subscribe({
      next: (res) => {
        this.askedAbout.set(symptoms);
        this.result.set(res);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(parseApiError(err).message);
        this.loading.set(false);
      },
    });
  }

  protected workload(d: DoctorMatch): string {
    if (d.upcomingBookings === 0) return 'Open schedule';
    return d.upcomingBookings === 1 ? '1 upcoming booking' : `${d.upcomingBookings} upcoming bookings`;
  }

  protected book(d: DoctorMatch): void {
    // booking reason is capped at 500 chars server-side
    this.drafts.set({ doctorId: d.id, reason: this.askedAbout().slice(0, 500) });
    this.router.navigateByUrl('/appointments/new');
  }
}
