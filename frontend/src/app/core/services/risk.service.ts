import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api.config';
import { Page, RiskHistoryItem, RiskRequest, RiskResponse } from '../models/models';

@Injectable({ providedIn: 'root' })
export class RiskService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_URL);

  assess(body: RiskRequest): Observable<RiskResponse> {
    return this.http.post<RiskResponse>(`${this.api}/risk/diabetes`, body);
  }

  history(page = 0, size = 10): Observable<Page<RiskHistoryItem>> {
    return this.http.get<Page<RiskHistoryItem>>(`${this.api}/risk/history`, { params: { page, size } });
  }

  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/risk/history/${id}`);
  }

  removeAll(): Observable<void> {
    return this.http.delete<void>(`${this.api}/risk/history`);
  }
}
