import { z } from 'zod';

/**
 * Institutional course / program code rules.
 *
 * Mirrors `com.lms.shared.InstitutionalCodes` on the server — DCS-UBIT codes
 * are hyphenated (BSCS-501, CS-363, CS-363L), so a client-side rule stricter
 * than the server's simply blocks valid input before it is ever sent. Keep the
 * two in step.
 */
export const CODE_PATTERN = /^[A-Za-z0-9]+([-_][A-Za-z0-9]+)*$/;

export const CODE_MESSAGE =
  'Letters and digits, optionally separated by single hyphens or underscores';

export const CODE_PLACEHOLDER = 'e.g. BSCS-501';

/** Zod field for an institutional code. Trims and upper-cases, as the server does. */
export const codeField = z
  .string()
  .trim()
  .min(1, 'Code required')
  .max(20, 'Max 20 characters')
  .regex(CODE_PATTERN, CODE_MESSAGE)
  .transform((value) => value.toUpperCase());
