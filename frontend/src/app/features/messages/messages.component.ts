import { DatePipe, TitleCasePipe } from '@angular/common';
import { Component, ElementRef, OnDestroy, OnInit, computed, inject, input, signal, viewChild } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ChatMessage, MessageThread } from '../../core/models/models';
import { MessageService } from '../../core/services/message.service';
import { parseApiError } from '../../core/utils/error.util';
import { SpinnerComponent } from '../../shared/components/spinner.component';

export const POLL_MS = 4000;

/**
 * Conversation between a patient and their doctor about one appointment. New messages are fetched
 * every few seconds (only ones newer than the last seen id), and polling pauses while the tab is hidden.
 */
@Component({
  selector: 'app-messages',
  imports: [DatePipe, TitleCasePipe, RouterLink, ReactiveFormsModule, SpinnerComponent],
  templateUrl: './messages.component.html',
  styleUrl: './messages.component.scss',
})
export class MessagesComponent implements OnInit, OnDestroy {
  /** Route parameter (withComponentInputBinding). */
  readonly id = input.required<string>();

  private readonly api = inject(MessageService);
  private readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');

  protected readonly state = signal<'loading' | 'error' | 'loaded'>('loading');
  protected readonly errorMessage = signal('');
  protected readonly thread = signal<MessageThread | null>(null);
  protected readonly messages = signal<ChatMessage[]>([]);
  protected readonly sending = signal(false);
  protected readonly sendError = signal<string | null>(null);
  protected readonly canSend = computed(() => this.thread()?.canSend ?? false);
  protected readonly input = new FormControl('', { nonNullable: true });

  private timer: ReturnType<typeof setInterval> | null = null;
  private polling = false;

  private get appointmentId(): number {
    return Number(this.id());
  }

  private get lastId(): number {
    const list = this.messages();
    return list.length ? list[list.length - 1].id : 0;
  }

  ngOnInit(): void {
    this.load();
    this.timer = setInterval(() => this.poll(), POLL_MS);
  }

  ngOnDestroy(): void {
    if (this.timer) clearInterval(this.timer);
  }

  protected load(): void {
    this.state.set('loading');
    this.api.thread(this.appointmentId).subscribe({
      next: (t) => {
        this.thread.set(t);
        this.messages.set(t.messages);
        this.state.set('loaded');
        this.scrollDown();
      },
      error: (err) => {
        this.errorMessage.set(parseApiError(err).message);
        this.state.set('error');
      },
    });
  }

  private poll(): void {
    if (this.state() !== 'loaded' || this.polling || document.hidden) return;
    this.polling = true;
    this.api.thread(this.appointmentId, this.lastId).subscribe({
      next: (t) => {
        this.polling = false;
        this.thread.set({ ...t, messages: this.messages() });
        this.append(t.messages);
      },
      error: () => (this.polling = false), // a failed poll is retried on the next tick
    });
  }

  protected send(): void {
    const body = this.input.value.trim();
    if (!body || this.sending() || !this.canSend()) return;
    this.sending.set(true);
    this.sendError.set(null);
    this.api.send(this.appointmentId, body).subscribe({
      next: (m) => {
        this.sending.set(false);
        this.input.reset('');
        this.append([m]);
      },
      error: (err) => {
        this.sending.set(false);
        this.sendError.set(parseApiError(err).message); // the draft stays in the box
      },
    });
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  /** Adds only messages not already shown (a poll and a send can overlap). */
  private append(incoming: ChatMessage[]): void {
    const seen = new Set(this.messages().map((m) => m.id));
    const fresh = incoming.filter((m) => !seen.has(m.id));
    if (!fresh.length) return;
    this.messages.update((list) => [...list, ...fresh].sort((a, b) => a.id - b.id));
    this.scrollDown();
  }

  private scrollDown(): void {
    setTimeout(() => {
      const el = this.scroller()?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }
}
