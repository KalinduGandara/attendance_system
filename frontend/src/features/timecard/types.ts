export type DailyTimeCardStatus = 'PRESENT' | 'ABSENT' | 'LEAVE' | 'HOLIDAY' | 'OFF' | 'PARTIAL';
export type PunchEventStatus = 'PROCESSED' | 'UNRESOLVED' | 'INVALID' | 'SUPERSEDED';
export type PunchEventType = 'CHECK_IN' | 'CHECK_OUT' | 'BREAK_START' | 'BREAK_END';
export type CredentialType = 'RFID' | 'QR' | 'MOBILE' | 'FACE' | 'FINGER' | 'PIN';
export type TimeCardEditChangeType =
  | 'PUNCH_ADD'
  | 'PUNCH_EDIT'
  | 'PUNCH_DELETE'
  | 'STATUS_CHANGE'
  | 'NOTE';

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

export interface TimeCardEdit {
  id: string;
  punchEventId: string | null;
  changeType: TimeCardEditChangeType;
  beforeJson: string | null;
  afterJson: string | null;
  reason: string;
  editedByUserId: string;
  editedAt: string;
}

export interface TimeCardDetail extends TimeCard {
  notes: string | null;
  punches: PunchEvent[];
  edits: TimeCardEdit[];
}

export interface TimeCardEditRequest {
  changeType: TimeCardEditChangeType;
  punchEventId?: string | null;
  eventType?: PunchEventType | null;
  newEventTime?: string | null;
  ingestionSourceId?: string | null;
  newStatus?: DailyTimeCardStatus | null;
  newNotes?: string | null;
  reason: string;
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
