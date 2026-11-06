import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Password must contain a letter and a number. (Length is checked by Validators.minLength.) */
export const passwordStrength: ValidatorFn = (c: AbstractControl): ValidationErrors | null => {
  const v: string = c.value ?? '';
  if (!v) return null; // let Validators.required report empty values
  return /[A-Za-z]/.test(v) && /\d/.test(v)
    ? null
    : { strength: 'Use at least one letter and one number.' };
};

/**
 * Group-level (cross-field) validator: compares two sibling controls.
 * Attached to the FormGroup, because a single control cannot see its siblings.
 */
export const fieldsMatch =
  (a: string, b: string): ValidatorFn =>
  (group: AbstractControl): ValidationErrors | null => {
    const first = group.get(a)?.value;
    const second = group.get(b)?.value;
    return first && second && first !== second ? { mismatch: 'Passwords do not match.' } : null;
  };

/** Date-time (from <input type="datetime-local">) must be in the future. */
export const futureDateTime: ValidatorFn = (c: AbstractControl): ValidationErrors | null => {
  if (!c.value) return null;
  return new Date(c.value).getTime() > Date.now()
    ? null
    : { past: 'Pick a date and time in the future.' };
};

/** Whole numbers only (null/empty is allowed; pair with Validators.required if needed). */
export const integer: ValidatorFn = (c: AbstractControl): ValidationErrors | null => {
  const v = c.value;
  return v === null || v === '' || Number.isInteger(Number(v)) ? null : { integer: 'Enter a whole number.' };
};

/** Group-level: at least one of the named controls must have a value. */
export const atLeastOneOf =
  (...names: string[]): ValidatorFn =>
  (group: AbstractControl): ValidationErrors | null =>
    names.some((n) => {
      const v = group.get(n)?.value;
      return v !== null && v !== undefined && v !== '';
    })
      ? null
      : { needOne: true };
