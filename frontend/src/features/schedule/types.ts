export type CycleType = 'DAILY' | 'WEEKLY';
export type AssignmentTargetType = 'EMPLOYEE' | 'GROUP';
export type ResolveSource = 'TEMPORARY' | 'EMPLOYEE_ASSIGNMENT' | 'GROUP_ASSIGNMENT' | 'NONE';

export interface TemplateDayRequest {
  dayIndex: number;
  shiftId: string | null;
}

export interface TemplateRequest {
  name: string;
  cycleType: CycleType;
  cycleLengthDays: number;
  description: string | null;
  days: TemplateDayRequest[];
  expectedVersion: number | null;
}

export interface TemplateDayResponse extends TemplateDayRequest {
  id: string;
}

export interface Template {
  id: string;
  name: string;
  cycleType: CycleType;
  cycleLengthDays: number;
  description: string | null;
  days: TemplateDayResponse[];
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface AssignmentRequest {
  targetType: AssignmentTargetType;
  targetId: string;
  templateId: string;
  startDate: string;
  endDate: string | null;
  priority: number | null;
  expectedVersion: number | null;
}

export interface Assignment {
  id: string;
  targetType: AssignmentTargetType;
  targetId: string;
  templateId: string;
  startDate: string;
  endDate: string | null;
  priority: number;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface TemporaryScheduleRequest {
  employeeId: string;
  startDate: string;
  endDate: string;
  shiftId: string | null;
  reason: string | null;
  expectedVersion: number | null;
}

export interface TemporarySchedule {
  id: string;
  employeeId: string;
  startDate: string;
  endDate: string;
  shiftId: string | null;
  reason: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface ResolvedScheduleDay {
  employeeId: string;
  date: string;
  source: ResolveSource;
  shiftId: string | null;
  templateId: string | null;
  dayIndex: number;
  assignmentId: string | null;
  temporaryScheduleId: string | null;
}
