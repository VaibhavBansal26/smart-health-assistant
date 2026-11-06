import { Pipe, PipeTransform } from '@angular/core';
import { nameParts } from '../utils/name.util';

/** Custom pipe: "Dr. Asha Verma" -> "AV". Usage: {{ user.fullName | initials }} */
@Pipe({ name: 'initials' })
export class InitialsPipe implements PipeTransform {
  transform(name: string | null | undefined): string {
    if (!name) {
      return '?';
    }
    const parts = nameParts(name);
    const first = parts[0]?.[0] ?? '';
    const last = parts.length > 1 ? parts[parts.length - 1][0] : '';
    return (first + last).toUpperCase() || '?';
  }
}
