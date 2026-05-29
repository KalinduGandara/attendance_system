export type ExceptionStatus = 'OPEN' | 'RESOLVED' | 'IGNORED';
export type ExceptionSeverity = 'INFO' | 'WARN' | 'CRITICAL';
export type ExceptionType =
  | 'MISSING_PUNCH_IN'
  | 'MISSING_PUNCH_OUT'
  | 'LATE_IN'
  | 'EARLY_OUT'
  | 'ABSENT_NO_LEAVE'
  | 'UNAUTHORIZED_OT'
  | 'ORPHAN_PUNCH';

export interface ExceptionEvent {
  id: string;
  employeeId: string;
  employeeName: string | null;
  dailyTimeCardId: string | null;
  workDate: string;
  exceptionType: ExceptionType;
  severity: ExceptionSeverity;
  detailsJson: string | null;
  status: ExceptionStatus;
  resolvedBy: string | null;
  resolvedAt: string | null;
  resolutionNote: string | null;
  version: number;
}

export interface ResolutionRequest {
  status: 'RESOLVED' | 'IGNORED';
  resolutionNote?: string | null;
}
