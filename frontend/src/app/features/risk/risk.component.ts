import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormControl, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { RiskFactor, RiskHistoryItem, RiskLevel, RiskResponse } from '../../core/models/models';
import { RiskService } from '../../core/services/risk.service';
import { SymptomDraftService } from '../../core/services/symptom-draft.service';
import { ToastService } from '../../core/services/toast.service';
import { parseApiError } from '../../core/utils/error.util';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog.component';
import { FieldErrorComponent } from '../../shared/components/field-error.component';
import { SpinnerComponent } from '../../shared/components/spinner.component';
import { atLeastOneOf, integer } from '../../shared/validators/validators';
import { RiskGaugeComponent } from './risk-gauge.component';

type LoadState = 'loading' | 'loaded' | 'error';

const HISTORY_PAGE_SIZE = 10;

@Component({
  selector: 'app-risk',
  imports: [
    ReactiveFormsModule, DatePipe, DecimalPipe, FieldErrorComponent, SpinnerComponent,
    ConfirmDialogComponent, RiskGaugeComponent,
  ],
  templateUrl: './risk.component.html',
  styleUrl: './risk.component.scss',
})
export class RiskComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly api = inject(RiskService);
  private readonly toast = inject(ToastService);
  private readonly drafts = inject(SymptomDraftService);
  private readonly router = inject(Router);

  protected readonly form = this.fb.group(
    {
      age: this.fb.control<number | null>(null, [Validators.required, Validators.min(21), Validators.max(100), integer]),
      glucose: this.fb.control<number | null>(null, [Validators.min(40), Validators.max(400)]),
      bmi: this.fb.control<number | null>(null, [Validators.min(12), Validators.max(70)]),
      pregnancies: this.fb.control<number | null>(null, [Validators.min(0), Validators.max(20), integer]),
    },
    { validators: atLeastOneOf('glucose', 'bmi') },
  );

  // optional BMI helper
  protected readonly heightCm = new FormControl<number | null>(null);
  protected readonly weightKg = new FormControl<number | null>(null);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly result = signal<RiskResponse | null>(null);

  protected readonly history = signal<RiskHistoryItem[]>([]);
  protected readonly historyState = signal<LoadState>('loading');
  protected readonly confirmClear = signal(false);
  protected readonly historyTotal = signal(0);
  protected readonly historyHasNext = signal(false);
  protected readonly loadingMore = signal(false);
  /** Index of the next history page to fetch for "Load more". */
  private nextHistoryPage = 1;

  /** Widest bar = 100%; every other factor is scaled against it, so bars are comparable. */
  protected readonly maxImpact = computed(() => {
    const r = this.result();
    return r ? Math.max(0.01, ...r.factors.map((f) => Math.abs(f.impact))) : 1;
  });

  protected readonly levelText: Record<RiskLevel, string> = {
    LOW: 'Lower risk',
    MODERATE: 'Moderate risk',
    HIGH: 'Higher risk',
  };
  protected readonly nextStep: Record<RiskLevel, string> = {
    LOW: 'Nothing here suggests urgent action. Regular check-ups are still a good idea.',
    MODERATE: 'It may be worth asking your doctor about a blood sugar test at your next visit.',
    HIGH: 'Please consider booking a check-up to get a proper blood sugar test.',
  };

  constructor() {
    this.loadHistory();
  }

  protected get bmiPreview(): number | null {
    const h = this.heightCm.value;
    const w = this.weightKg.value;
    if (!h || !w || h < 100 || h > 250 || w < 25 || w > 300) return null;
    return Math.round((w / (h / 100) ** 2) * 10) / 10;
  }

  protected useBmi(): void {
    const bmi = this.bmiPreview;
    if (bmi !== null) {
      this.form.controls.bmi.setValue(bmi);
      this.form.controls.bmi.markAsDirty();
    }
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.loading.set(true);
    this.error.set(null);
    this.result.set(null);

    this.api
      .assess({ age: v.age!, glucose: v.glucose, bmi: v.bmi, pregnancies: v.pregnancies })
      .subscribe({
        next: (res) => {
          this.result.set(res);
          this.loading.set(false);
          this.reloadHistory();
        },
        error: (err) => {
          const e = parseApiError(err);
          for (const [field, msg] of Object.entries(e.fieldErrors)) {
            this.form.get(field)?.setErrors({ server: msg });
          }
          this.error.set(e.message);
          this.loading.set(false);
        },
      });
  }

  protected loadHistory(): void {
    this.historyState.set('loading');
    this.fetchFirstPage(() => this.historyState.set('error'));
  }

  /** Quietly re-reads the first page (after adding or deleting) without a loading skeleton. */
  private reloadHistory(): void {
    this.fetchFirstPage(() => undefined);
  }

  private fetchFirstPage(onError: () => void): void {
    this.api.history(0, HISTORY_PAGE_SIZE).subscribe({
      next: (page) => {
        this.history.set(page.content);
        this.historyTotal.set(page.totalElements);
        this.historyHasNext.set(page.hasNext);
        this.nextHistoryPage = 1;
        this.historyState.set('loaded');
      },
      error: onError,
    });
  }

  protected loadMore(): void {
    this.loadingMore.set(true);
    this.api.history(this.nextHistoryPage, HISTORY_PAGE_SIZE).subscribe({
      next: (page) => {
        const have = new Set(this.history().map((h) => h.id));
        this.history.update((h) => [...h, ...page.content.filter((x) => !have.has(x.id))]);
        this.historyTotal.set(page.totalElements);
        this.historyHasNext.set(page.hasNext);
        this.nextHistoryPage++;
        this.loadingMore.set(false);
      },
      error: (err) => {
        this.loadingMore.set(false);
        this.toast.error(parseApiError(err).message);
      },
    });
  }

  protected remove(item: RiskHistoryItem): void {
    this.api.remove(item.id).subscribe({
      next: () => this.reloadHistory(),
      error: (err) => this.toast.error(parseApiError(err).message),
    });
  }

  protected clearAll(): void {
    this.confirmClear.set(false);
    this.api.removeAll().subscribe({
      next: () => {
        this.history.set([]);
        this.historyTotal.set(0);
        this.historyHasNext.set(false);
        this.toast.success('Your saved checks were deleted.');
      },
      error: (err) => this.toast.error(parseApiError(err).message),
    });
  }

  protected findDoctor(): void {
    this.drafts.set('I would like a diabetes check-up and a blood sugar test');
    this.router.navigateByUrl('/recommend');
  }

  protected barWidth(f: RiskFactor): number {
    return (Math.abs(f.impact) / this.maxImpact()) * 50; // each side of the centre line is 50%
  }

  protected formatValue(f: RiskFactor): string {
    return f.value === null ? 'not entered' : `${f.value} ${f.unit === 'count' ? '' : f.unit}`.trim();
  }

  protected isMissing(name: string): boolean {
    return this.result()?.missing.includes(name) ?? false;
  }
}
