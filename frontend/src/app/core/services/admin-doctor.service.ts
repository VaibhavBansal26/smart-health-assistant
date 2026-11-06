import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api.config';
import { AdminDoctor, CreateDoctorPayload, Page, UpdateDoctorPayload } from '../models/models';

/** Administrator-only calls for managing doctor accounts. */
@Injectable({ providedIn: 'root' })
export class AdminDoctorService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_URL);

  list(options: { q?: string; active?: boolean | null; page?: number; size?: number }): Observable<Page<AdminDoctor>> {
    let params = new HttpParams().set('page', options.page ?? 0).set('size', options.size ?? 10);
    if (options.q?.trim()) params = params.set('q', options.q.trim());
    if (options.active !== null && options.active !== undefined) params = params.set('active', options.active);
    return this.http.get<Page<AdminDoctor>>(`${this.api}/admin/doctors`, { params });
  }

  create(payload: CreateDoctorPayload): Observable<AdminDoctor> {
    return this.http.post<AdminDoctor>(`${this.api}/admin/doctors`, payload);
  }

  update(id: number, payload: UpdateDoctorPayload): Observable<AdminDoctor> {
    return this.http.patch<AdminDoctor>(`${this.api}/admin/doctors/${id}`, payload);
  }

  resendInvite(id: number): Observable<void> {
    return this.http.post<void>(`${this.api}/admin/doctors/${id}/resend-invite`, {});
  }
}
