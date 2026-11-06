import { FormControl, FormGroup } from '@angular/forms';
import { atLeastOneOf, fieldsMatch, futureDateTime, integer, passwordStrength } from './validators';

describe('validators', () => {
  it('passwordStrength needs a letter and a number, and ignores empty values', () => {
    expect(passwordStrength(new FormControl(''))).toBeNull();
    expect(passwordStrength(new FormControl('abcdefgh'))).not.toBeNull();
    expect(passwordStrength(new FormControl('12345678'))).not.toBeNull();
    expect(passwordStrength(new FormControl('abcd1234'))).toBeNull();
  });

  it('fieldsMatch flags two different values but not empty or equal ones', () => {
    const group = (a: string, b: string) => new FormGroup({ password: new FormControl(a), confirm: new FormControl(b) });
    const v = fieldsMatch('password', 'confirm');
    expect(v(group('abc12345', 'abc12345'))).toBeNull();
    expect(v(group('abc12345', ''))).toBeNull(); // "required" reports the empty one
    expect(v(group('abc12345', 'different1'))).toEqual({ mismatch: 'Passwords do not match.' });
  });

  it('futureDateTime rejects the past, accepts the future, ignores empty', () => {
    // <input type="datetime-local"> values are LOCAL time without a zone, so build them from local parts
    const local = (offsetMs: number) => {
      const d = new Date(Date.now() + offsetMs);
      const p = (n: number) => String(n).padStart(2, '0');
      return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
    };
    expect(futureDateTime(new FormControl(''))).toBeNull();
    expect(futureDateTime(new FormControl(local(-3_600_000)))).not.toBeNull();
    expect(futureDateTime(new FormControl(local(3_600_000 * 24)))).toBeNull();
  });

  it('integer accepts whole numbers and empty, rejects fractions', () => {
    expect(integer(new FormControl(null))).toBeNull();
    expect(integer(new FormControl(''))).toBeNull();
    expect(integer(new FormControl(0))).toBeNull();
    expect(integer(new FormControl(42))).toBeNull();
    expect(integer(new FormControl(4.5))).not.toBeNull();
  });

  it('atLeastOneOf needs one of the named fields filled (0 counts as filled)', () => {
    const v = atLeastOneOf('glucose', 'bmi');
    const g = (glucose: unknown, bmi: unknown) => new FormGroup({ glucose: new FormControl(glucose), bmi: new FormControl(bmi) });
    expect(v(g(null, null))).toEqual({ needOne: true });
    expect(v(g('', ''))).toEqual({ needOne: true });
    expect(v(g(120, null))).toBeNull();
    expect(v(g(null, 0))).toBeNull();
  });
});
