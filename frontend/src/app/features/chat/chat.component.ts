import { Component, ElementRef, effect, inject, viewChild } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ChatStore } from '../../core/services/chat.store';
import { SymptomDraftService } from '../../core/services/symptom-draft.service';

@Component({
  selector: 'app-chat',
  imports: [ReactiveFormsModule],
  templateUrl: './chat.component.html',
  styleUrl: './chat.component.scss',
})
export class ChatComponent {
  protected readonly store = inject(ChatStore);
  private readonly drafts = inject(SymptomDraftService);
  private readonly router = inject(Router);

  protected readonly input = new FormControl('', { nonNullable: true });
  private readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');
  /** Auto-scroll only while the user is at the bottom, so reading older messages is not interrupted. */
  private stickToBottom = true;

  protected readonly starters = [
    'I have had a headache for three days',
    'How do I know if my cough needs a doctor?',
    'What should I ask my doctor about high blood pressure?',
  ];

  constructor() {
    this.store.loadStatus();
    effect(() => {
      this.store.messages(); // re-run whenever messages change (including each streamed chunk)
      setTimeout(() => this.scrollToBottom());
    });
  }

  protected send(text = this.input.value): void {
    const t = text.trim();
    if (!t || this.store.sending()) return;
    this.input.reset();
    this.stickToBottom = true;
    this.store.send(t);
  }

  protected onKeydown(e: KeyboardEvent): void {
    // Enter sends, Shift+Enter makes a new line; ignore Enter that confirms IME text (e.g. Japanese input)
    if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
      e.preventDefault();
      this.send();
    }
  }

  protected onScroll(): void {
    const el = this.scroller()?.nativeElement;
    if (el) this.stickToBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 80;
  }

  protected findDoctor(): void {
    this.drafts.set(this.store.userText().slice(0, 1000));
    this.router.navigateByUrl('/recommend');
  }

  private scrollToBottom(): void {
    const el = this.scroller()?.nativeElement;
    if (el && this.stickToBottom) el.scrollTop = el.scrollHeight;
  }
}
