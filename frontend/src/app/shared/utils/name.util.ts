const TITLE = /^(dr|mr|mrs|ms|prof)\.?\s+/i;

/** "Dr. Asha Verma" -> ["Asha", "Verma"] */
export function nameParts(fullName: string): string[] {
  return fullName.replace(TITLE, '').split(/\s+/).filter(Boolean);
}

/** "Dr. Asha Verma" -> "Asha" (keeps the title if there is nothing else) */
export function firstName(fullName: string | null | undefined): string {
  if (!fullName) return '';
  return nameParts(fullName)[0] ?? fullName;
}
