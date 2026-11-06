import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  imports: [RouterLink],
  template: `
    <div class="wrap">
      <h1>404</h1>
      <p class="muted">That page does not exist.</p>
      <a class="btn" routerLink="/dashboard">Back to dashboard</a>
    </div>
  `,
  styles: `
    .wrap { min-height: 100vh; display: grid; place-content: center; justify-items: center; gap: 12px; text-align: center; }
    h1 { font-size: 4rem; color: var(--primary); }
  `,
})
export class NotFoundComponent {}
