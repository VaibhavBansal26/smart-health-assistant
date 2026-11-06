import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { API_URL } from '../api.config';
import { Page, User } from '../models/models';

/** Plain request/response service: no state, just returns Observables. */
@Injectable({ providedIn: 'root' })
export class DoctorService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_URL);

  /** The booking picker shows everyone at once, so it asks for the largest page the API allows (100). */
  list(): Observable<User[]> {
    return this.http
      .get<Page<User>>(`${this.api}/doctors`, { params: { size: 100 } })
      .pipe(map((page) => page.content));
  }
}
