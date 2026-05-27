export type ShiftType = 'FIXED' | 'FLEXIBLE' | 'FLOATING';
export type RoundingKind = 'SHIFT' | 'PUNCH_IN' | 'PUNCH_OUT';
export type RoundingMode = 'UP' | 'DOWN' | 'NEAREST';
export type GraceKind = 'LATE_IN' | 'EARLY_OUT';
export type BreakKind = 'AUTO_DEDUCT' | 'PUNCH_TRACKED';

export interface SegmentRequest {
  segmentOrder: number;
  startMinuteOfDay: number;
  endMinuteOfDay: number;
  requiredMinutes: number | null;
}

export interface RoundingRuleRequest {
  kind: RoundingKind;
  unitMinutes: number;
  mode: RoundingMode;
}

export interface GraceRuleRequest {
  kind: GraceKind;
  minutes: number;
}

export interface BreakRuleRequest {
  name: string;
  kind: BreakKind;
  durationMinutes: number;
  earliestStartMinute: number | null;
  afterHoursWorked: number | null;
  paid: boolean;
  timeCodeId: string | null;
}

export interface OvertimeRuleRequest {
  sequenceOrder: number;
  afterMinutesWorked: number;
  timeCodeId: string;
  maxMinutes: number | null;
}

export interface ShiftRequest {
  name: string;
  shiftType: ShiftType;
  color: string;
  timezone: string | null;
  description: string | null;
  active: boolean;
  attendanceTimeCodeId: string;
  segments: SegmentRequest[];
  roundingRules: RoundingRuleRequest[];
  graceRules: GraceRuleRequest[];
  breakRules: BreakRuleRequest[];
  overtimeRules: OvertimeRuleRequest[];
  candidateShiftIds: string[];
  expectedVersion: number | null;
}

export interface SegmentResponse extends SegmentRequest {
  id: string;
}
export interface RoundingRuleResponse extends RoundingRuleRequest {
  id: string;
}
export interface GraceRuleResponse extends GraceRuleRequest {
  id: string;
}
export interface BreakRuleResponse extends BreakRuleRequest {
  id: string;
}
export interface OvertimeRuleResponse extends OvertimeRuleRequest {
  id: string;
}

export interface Shift {
  id: string;
  name: string;
  shiftType: ShiftType;
  color: string;
  timezone: string | null;
  description: string | null;
  active: boolean;
  attendanceTimeCodeId: string;
  segments: SegmentResponse[];
  roundingRules: RoundingRuleResponse[];
  graceRules: GraceRuleResponse[];
  breakRules: BreakRuleResponse[];
  overtimeRules: OvertimeRuleResponse[];
  candidateShiftIds: string[];
  createdAt: string;
  updatedAt: string;
  version: number;
}
