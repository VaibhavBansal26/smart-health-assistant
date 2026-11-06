import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api.config';
import { RecommendResponse } from '../models/models';

@Injectable({ providedIn: 'root' })
export class RecommendationService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_URL);

  recommend(symptoms: string): Observable<RecommendResponse> {
    return this.http.post<RecommendResponse>(`${this.api}/recommendations`, { symptoms });
  }
}
