import { AxiosError } from 'axios';
import type { ProblemDetail } from './types';

export function describeApiError(err: unknown, fallback = 'Request failed'): string {
  if (err instanceof AxiosError) {
    const data = err.response?.data as ProblemDetail | undefined;
    if (data?.errors && data.errors.length > 0) {
      return data.errors.map((e) => `${e.field}: ${e.message}`).join('; ');
    }
    if (data?.detail) {
      return data.detail;
    }
    if (data?.title) {
      return data.title;
    }
    return err.message;
  }
  if (err instanceof Error) {
    return err.message;
  }
  return fallback;
}
