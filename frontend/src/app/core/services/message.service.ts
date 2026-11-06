import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api.config';
import { ChatMessage, MessageThread } from '../models/models';

/** The private thread between the patient and the doctor of one appointment. */
@Injectable({ providedIn: 'root' })
export class MessageService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_URL);

  /** Pass the id of the newest message you already have to receive only newer ones. */
  thread(appointmentId: number, after = 0): Observable<MessageThread> {
    const params = new HttpParams().set('after', after);
    return this.http.get<MessageThread>(`${this.api}/appointments/${appointmentId}/messages`, { params });
  }

  send(appointmentId: number, body: string): Observable<ChatMessage> {
    return this.http.post<ChatMessage>(`${this.api}/appointments/${appointmentId}/messages`, { body });
  }
}
