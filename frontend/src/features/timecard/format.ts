import type { DailyTimeCardStatus, PunchEventStatus, PunchEventType } from './types';

export const STATUS_COLORS: Record<DailyTimeCardStatus, string> = {
  PRESENT: 'green',
  ABSENT: 'red',
  LEAVE: 'blue',
  HOLIDAY: 'teal',
  OFF: 'gray',
  PARTIAL: 'yellow'
};

export const PUNCH_STATUS_COLORS: Record<PunchEventStatus, string> = {
  PROCESSED: 'green',
  UNRESOLVED: 'orange',
  INVALID: 'red',
  SUPERSEDED: 'gray'
};

export const PUNCH_TYPE_LABELS: Record<PunchEventType, string> = {
  CHECK_IN: 'In',
  CHECK_OUT: 'Out',
  BREAK_START: 'Break start',
  BREAK_END: 'Break end'
};

export function fmtMinutes(m: number): string {
  if (m === 0) return '—';
  const hours = Math.floor(m / 60);
  const minutes = m % 60;
  return `${hours}h${minutes.toString().padStart(2, '0')}`;
}

export function fmtInstantLocal(iso: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleString();
}

export function fmtTimeLocal(iso: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

export function toDateInputString(d: Date): string {
  // YYYY-MM-DD in local time, suitable for the backend `LocalDate` fields.
  const y = d.getFullYear();
  const m = (d.getMonth() + 1).toString().padStart(2, '0');
  const day = d.getDate().toString().padStart(2, '0');
  return `${y}-${m}-${day}`;
}
