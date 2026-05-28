export type DailyTimeCardStatus = 'PRESENT' | 'ABSENT' | 'LEAVE' | 'HOLIDAY' | 'OFF' | 'PARTIAL';
export type PunchEventStatus = 'PROCESSED' | 'UNRESOLVED' | 'INVALID' | 'SUPERSEDED';
export type PunchEventType = 'CHECK_IN' | 'CHECK_OUT' | 'BREAK_START' | 'BREAK_END';
export type CredentialType = 'RFID' | 'QR' | 'MOBILE' | 'FACE' | 'FINGER' | 'PIN';

export interface EmployeeRef {
  id: string;
  code: string;
  name: string;
}

export interface ShiftRef {
  id: string;
  name: string;
  color: string;
}

export interface Breakdown {
  timeCode: string | null;
  timeCodeId: string;
  minutes: number;
  ratedMinutes: number;
  sequenceOrder: number;
}

export interface ExceptionRef {
  id: string;
  type: string;
  severity: string;
  status: string;
}

export interface TimeCard {
  id: string;
  employee: EmployeeRef | null;
  workDate: string;
  status: DailyTimeCardStatus;
  resolvedShift: ShiftRef | null;
  scheduledStart: string | null;
  scheduledEnd: string | null;
  actualStart: string | null;
  actualEnd: string | null;
  workedMinutes: number;
  breakMinutes: number;
  overtimeMinutes: number;
  lateMinutes: number;
  earlyOutMinutes: number;
  breakdown: Breakdown[];
  exceptions: ExceptionRef[];
  computedAt: string;
  version: number;
}

export interface PunchEvent {
  id: string;
  employeeId: string | null;
  deviceId: string | null;
  ingestionSourceId: string;
  externalEventId: string;
  eventType: PunchEventType;
  eventTimeUtc: string;
  status: PunchEventStatus;
  processedAt: string | null;
}

export interface IngestionEventResult {
  externalEventId: string;
  status: 'ACCEPTED' | 'DUPLICATE' | 'UNRESOLVED_CREDENTIAL' | 'INVALID';
  punchEventId: string | null;
  detail: string | null;
}

export interface IngestionResponse {
  accepted: number;
  duplicate: number;
  unresolved: number;
  invalid: number;
  results: IngestionEventResult[];
}

export interface PunchBatchRequest {
  sourceId: string;
  events: Array<{
    externalEventId: string;
    eventType: PunchEventType;
    eventTime: string;
    credential?: { type: CredentialType; value: string };
    deviceId?: string;
    employeeId?: string;
  }>;
}

export interface RecomputeRequest {
  employeeIds?: string[];
  from: string;
  to: string;
}

export interface RecomputeResponse {
  recomputedDays: number;
}
