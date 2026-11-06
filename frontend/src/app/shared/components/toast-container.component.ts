import { Component, inject } from '@angular/core';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-toast-container',
  template: `
    <div class="toasts" aria-live="polite">
      @for (t of toasts.toasts(); track t.id) {
        <div class="toast" [class]="'toast ' + t.kind" role="status">
          <span>{{ t.message }}</span>
          <button type="button" class="close" aria-label="Dismiss" (click)="toasts.dismiss(t.id)">×</button>
        </div>
      }
    </div>
  `,
  styles: `
    .toasts { position: fixed; right: 16px; bottom: 16px; display: flex; flex-direction: column;
      gap: 8px; z-index: 1000; max-width: min(360px, calc(100vw - 32px)); }
    .toast { display: flex; align-items: flex-start; gap: 12px; padding: 12px 14px; border-radius: 10px;
      color: #fff; background: #334155; box-shadow: 0 8px 24px rgb(15 23 42 / 25%); animation: in 0.2s ease; }
    .toast.success { background: #15803d; }
    .toast.error { background: #b91c1c; }
    .close { margin-left: auto; background: none; border: 0; color: inherit; font-size: 18px;
      line-height: 1; cursor: pointer; opacity: 0.8; }
    @keyframes in { from { opacity: 0; transform: translateY(8px); } }
  `,
})
export class ToastContainerComponent {
  protected readonly toasts = inject(ToastService);
}
