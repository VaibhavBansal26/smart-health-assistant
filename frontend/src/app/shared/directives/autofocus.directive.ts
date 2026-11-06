import { Directive, ElementRef, afterNextRender, inject } from '@angular/core';

/** Attribute directive: focuses the element once it is rendered. Usage: <input appAutofocus /> */
@Directive({ selector: '[appAutofocus]' })
export class AutofocusDirective {
  constructor() {
    const el = inject<ElementRef<HTMLElement>>(ElementRef);
    afterNextRender(() => el.nativeElement.focus());
  }
}
