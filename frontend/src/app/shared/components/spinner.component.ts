import { Component } from '@angular/core';

/** Tiny inline loading spinner, e.g. inside a submit button. */
@Component({
  selector: 'app-spinner',
  template: `<span class="spin" role="progressbar" aria-label="Loading"></span>`,
  styles: `
    .spin { display: inline-block; width: 1em; height: 1em; border-radius: 50%;
      border: 2px solid currentColor; border-right-color: transparent; animation: r 0.7s linear infinite; }
    @keyframes r { to { transform: rotate(360deg); } }
    @media (prefers-reduced-motion: reduce) { .spin { animation-duration: 2s; } }
  `,
})
export class SpinnerComponent {}
