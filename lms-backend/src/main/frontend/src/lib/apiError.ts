import axios from 'axios';

/**
 * Reads the RFC 7807 problem details the API returns (see
 * `GlobalExceptionHandler` on the backend) so callers can show the server's own
 * message instead of a fixed "something went wrong".
 */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  /** Per-field messages, present on validation failures. */
  errors?: Record<string, string>;
}

function problemOf(error: unknown): ProblemDetail | undefined {
  if (!axios.isAxiosError(error)) return undefined;
  const data = error.response?.data;
  if (!data || typeof data !== 'object') return undefined;
  return data as ProblemDetail;
}

/** Field name → message, for painting errors onto the inputs that caused them. */
export function fieldErrors(error: unknown): Record<string, string> {
  return problemOf(error)?.errors ?? {};
}

/** HTTP status, when the failure reached the server at all. */
export function statusOf(error: unknown): number | undefined {
  return axios.isAxiosError(error) ? error.response?.status : undefined;
}

/**
 * A message worth showing a user. Prefers the server's `detail`, falls back to
 * a status-specific line, and finally to a network-level message.
 */
export function parseApiError(error: unknown, fallback = 'Something went wrong.'): string {
  if (!error) return fallback;

  const problem = problemOf(error);
  if (problem?.detail && problem.detail !== 'Invalid request content.') {
    return problem.detail;
  }
  if (problem?.errors && Object.keys(problem.errors).length > 0) {
    return Object.entries(problem.errors)
      .map(([field, message]) => `${field}: ${message}`)
      .join('; ');
  }

  if (axios.isAxiosError(error)) {
    if (error.code === 'ECONNABORTED') {
      return 'The request timed out. Please try again.';
    }
    if (!error.response) {
      return 'Cannot reach the server. Check your connection and try again.';
    }
    switch (error.response.status) {
      case 400: return 'The submitted data was rejected. Please review the form.';
      case 401: return 'Your session has expired. Please sign in again.';
      case 403: return 'You do not have permission to do that.';
      case 404: return 'That item no longer exists.';
      case 409: return problem?.title ?? 'That action conflicts with the current state.';
      case 413: return 'The file is too large to upload.';
      case 429: return 'Too many requests. Please wait a moment and retry.';
      default:
        if (error.response.status >= 500) {
          return 'The server had a problem completing that request. Please try again.';
        }
    }
  }

  // Anything else is a bug in our own code ("Cannot read properties of
  // undefined …"), which means nothing to a user. Keep it for developers.
  if (!axios.isAxiosError(error)) {
    console.error('Non-HTTP error reported to the user as a fallback:', error);
  }
  return fallback;
}
