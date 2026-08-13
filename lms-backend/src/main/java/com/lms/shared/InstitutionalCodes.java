package com.lms.shared;

/**
 * The grammar for institutional course and program codes.
 *
 * <p>DCS-UBIT writes course codes with a hyphen — {@code BSCS-501},
 * {@code CS-363}, {@code CS-363L} for the lab section — while older records use
 * the unseparated form ({@code CS101}). The previous rule allowed neither
 * hyphens nor lower-case input, so none of the department's real codes could be
 * entered at all.
 *
 * <p>The pattern below accepts alphanumeric segments joined by single hyphens
 * or underscores. It deliberately does not pin the exact segment widths (e.g.
 * {@code [A-Z]{2,6}-[0-9]{3}}): that would reject lab suffixes, legacy codes
 * and any future scheme, for no gain over the uniqueness constraint the
 * database already enforces. Rejected: leading/trailing separators, doubled
 * separators and whitespace. Case is accepted either way and stored upper-case
 * via {@link #normalise}, so {@code cs-363} and {@code CS-363} are one course.
 */
public final class InstitutionalCodes {

    /** Regex for a valid code. Case-insensitive; {@link #normalise} upper-cases for storage. */
    public static final String CODE_PATTERN = "^[A-Za-z0-9]+([-_][A-Za-z0-9]+)*$";

    public static final String CODE_MESSAGE =
            "Code must be letters and digits, optionally separated by single "
            + "hyphens or underscores (e.g. BSCS-501, CS-363L, MATH_101)";

    /** Upper-cases and trims a user-supplied code; null-safe. */
    public static String normalise(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }

    private InstitutionalCodes() {
        // utility class — no instances
    }
}
