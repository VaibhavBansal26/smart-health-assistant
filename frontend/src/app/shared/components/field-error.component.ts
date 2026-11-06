import { Component, input } from '@angular/core';
import { AbstractControl } from '@angular/forms';

/**
 * Shows the first validation message of a form control, but only after the
 * user has touched it (so an untouched form isn't a wall of red text).
 *
 * Usage: <app-field-error [control]="form.controls.email" />
 */
@Component({
  selector: 'app-field-error',
  template: `@if (message(); as m) {<p class="field-error" role="alert">{{ m }}</p>}`,
})
export class FieldErrorComponent {
  readonly control = input.required<AbstractControl>();

  protected message(): string | null {
    const c = this.control();
    if (!c.errors || !(c.touched || c.dirty)) {
      return null;
    }
    const e = c.errors;
    if (e['required']) return 'This field is required.';
    if (e['email']) return 'Enter a valid email address.';
    if (e['minlength']) return `Use at least ${e['minlength'].requiredLength} characters.`;
    if (e['min']) return `Must be ${e['min'].min} or more.`;
    if (e['max']) return `Must be ${e['max'].max} or less.`;
    if (e['maxlength']) return `Use at most ${e['maxlength'].requiredLength} characters.`;
    // custom validators / server errors supply their own text via { key: 'message' }
    const custom = Object.values(e).find((v) => typeof v === 'string');
    return (custom as string | undefined) ?? 'Invalid value.';
  }
}
