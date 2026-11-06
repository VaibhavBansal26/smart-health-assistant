import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, finalize, tap } from 'rxjs';
import { API_URL } from '../api.config';
import {
  Appointment,
  AppointmentStatus,
  AppointmentSummary,
  CreateAppointmentPayload,
  Page,
  UpdateAppointmentPayload,
} from '../models/models';
import { parseApiError } from '../utils/error.util';

export type LoadStatus = 'idle' | 'loading' | 'loaded' | 'error';
export type StatusFilter = 'ALL' | AppointmentStatus;

export const PAGE_SIZE = 10;

export interface PageInfo {
  page: number;
  totalPages: number;
  totalElements: number;
}

/**
 * Signal-based store for appointments, now backed by SERVER-SIDE paging and filtering.
 *
 *  - the list screen asks the server for one page at a time (status filter included)
 *  - the dashboard asks for a small summary (counts + next appointment) instead of every row
 *  - any change (book / cancel / complete / reschedule) refreshes both, silently,
 *    so every screen shows the truth without flashing loading skeletons
 */
@Injectable({ providedIn: 'root' })
export class AppointmentStore {
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_URL);

  // ---- list state ----
  private readonly _items = signal<Appointment[]>([]);
  private readonly _pageInfo = signal<PageInfo>({ page: 0, totalPages: 0, totalElements: 0 });
  private readonly _status = signal<LoadStatus>('idle');
  private readonly _error = signal<string | null>(null);
  private readonly _filter = signal<StatusFilter>('ALL');
  private readonly _busyIds = signal<ReadonlySet<number>>(new Set());

  // ---- summary state (dashboard) ----
  private readonly _summary = signal<AppointmentSummary | null>(null);
  private readonly _summaryStatus = signal<LoadStatus>('idle');
  private readonly _summaryError = signal<string | null>(null);

  readonly items = this._items.asReadonly();
  readonly pageInfo = this._pageInfo.asReadonly();
  readonly status = this._status.asReadonly();
  readonly error = this._error.asReadonly();
  readonly filter = this._filter.asReadonly();
  readonly summaryStatus = this._summaryStatus.asReadonly();
  readonly summaryError = this._summaryError.asReadonly();

  readonly counts = computed(() => {
    const s = this._summary();
    return { total: s?.total ?? 0, booked: s?.booked ?? 0, completed: s?.completed ?? 0, cancelled: s?.cancelled ?? 0 };
  });
  readonly next = computed(() => this._summary()?.next ?? null);

  /** Guards against slow, out-of-order responses (e.g. clicking filters quickly). */
  private listRequest = 0;
  private summaryRequest = 0;

  isBusy(id: number): boolean {
    return this._busyIds().has(id);
  }

  // ---- list ----
  setFilter(f: StatusFilter): void {
    if (f === this._filter()) return;
    this._filter.set(f);
    this.loadPage(0);
  }

  /** Loads one page. `silent` keeps current content on screen instead of showing a skeleton. */
  loadPage(page = this._pageInfo().page, silent = false): void {
    const request = ++this.listRequest;
    if (!silent) {
      this._status.set('loading');
    }
    this._error.set(null);

    let params = new HttpParams().set('page', page).set('size', PAGE_SIZE);
    if (this._filter() !== 'ALL') {
      params = params.set('status', this._filter());
    }

    this.http.get<Page<Appointment>>(`${this.api}/appointments`, { params }).subscribe({
      next: (res) => {
        if (request !== this.listRequest) return; // a newer request superseded this one
        // deleted the last item of the last page? step back instead of showing an empty page
        if (res.content.length === 0 && res.page > 0 && res.totalPages > 0) {
          this.loadPage(res.totalPages - 1, silent);
          return;
        }
        this._items.set(res.content);
        this._pageInfo.set({ page: res.page, totalPages: res.totalPages, totalElements: res.totalElements });
        this._status.set('loaded');
      },
      error: (err) => {
        if (request !== this.listRequest) return;
        this._error.set(parseApiError(err).message);
        this._status.set('error');
      },
    });
  }

  // ---- summary ----
  loadSummary(silent = false): void {
    const request = ++this.summaryRequest;
    if (!silent) {
      this._summaryStatus.set('loading');
    }
    this._summaryError.set(null);
    this.http.get<AppointmentSummary>(`${this.api}/appointments/summary`).subscribe({
      next: (s) => {
        if (request !== this.summaryRequest) return;
        this._summary.set(s);
        this._summaryStatus.set('loaded');
      },
      error: (err) => {
        if (request !== this.summaryRequest) return;
        this._summaryError.set(parseApiError(err).message);
        this._summaryStatus.set('error');
      },
    });
  }

  // ---- changes ----
  create(payload: CreateAppointmentPayload): Observable<Appointment> {
    return this.http.post<Appointment>(`${this.api}/appointments`, payload).pipe(tap(() => this.refresh()));
  }

  /** PATCH: reschedule and/or change the reason. */
  update(id: number, payload: UpdateAppointmentPayload): Observable<Appointment> {
    return this.change(this.http.patch<Appointment>(`${this.api}/appointments/${id}`, payload), id);
  }

  cancel(id: number): Observable<Appointment> {
    return this.change(this.http.patch<Appointment>(`${this.api}/appointments/${id}/cancel`, {}), id);
  }

  complete(id: number): Observable<Appointment> {
    return this.change(this.http.patch<Appointment>(`${this.api}/appointments/${id}/complete`, {}), id);
  }

  /** Clears everything, e.g. on logout so the next user never sees old data. */
  reset(): void {
    this.listRequest++;
    this.summaryRequest++;
    this._items.set([]);
    this._pageInfo.set({ page: 0, totalPages: 0, totalElements: 0 });
    this._status.set('idle');
    this._error.set(null);
    this._filter.set('ALL');
    this._busyIds.set(new Set());
    this._summary.set(null);
    this._summaryStatus.set('idle');
    this._summaryError.set(null);
  }

  /** After any change, re-fetch whatever has been loaded before. */
  private refresh(): void {
    if (this._status() !== 'idle') this.loadPage(this._pageInfo().page, true);
    if (this._summaryStatus() !== 'idle') this.loadSummary(true);
  }

  private change(request: Observable<Appointment>, id: number): Observable<Appointment> {
    this.setBusy(id, true);
    return request.pipe(
      tap(() => this.refresh()),
      finalize(() => this.setBusy(id, false)),
    );
  }

  private setBusy(id: number, busy: boolean): void {
    this._busyIds.update((set) => {
      const next = new Set(set);
      if (busy) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  }
}
