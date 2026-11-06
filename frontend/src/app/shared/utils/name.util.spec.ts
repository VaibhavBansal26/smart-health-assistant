import { InitialsPipe } from '../pipes/initials.pipe';
import { firstName, nameParts } from './name.util';

describe('name helpers', () => {
  it('firstName skips titles (the "Hello, Dr." bug)', () => {
    expect(firstName('Dr. Asha Verma')).toBe('Asha');
    expect(firstName('dr Asha Verma')).toBe('Asha');
    expect(firstName('Prof. Lee')).toBe('Lee');
    expect(firstName('Maya Patel')).toBe('Maya');
  });

  it('copes with empty and title-only input', () => {
    expect(firstName(null)).toBe('');
    expect(firstName('')).toBe('');
    expect(nameParts('Dr.')).toEqual(['Dr.']);
  });

  it('InitialsPipe uses first and last name, ignoring titles', () => {
    const pipe = new InitialsPipe();
    expect(pipe.transform('Dr. Asha Verma')).toBe('AV');
    expect(pipe.transform('Maya')).toBe('M');
    expect(pipe.transform('Mary Jane Watson')).toBe('MW');
    expect(pipe.transform(undefined)).toBe('?');
  });
});
