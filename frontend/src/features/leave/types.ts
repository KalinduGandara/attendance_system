export type LeaveRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
export type HalfDayPart = 'FIRST_HALF' | 'SECOND_HALF';

export interface LeaveType {
  id: string;
  timeCodeId: string;
  timeCodeCode: string | null;
  name: string;
  defaultAnnualDays: number | string;
  requiresApproval: boolean;
  accrualRuleJson: string | null;
  active: boolean;
  version: number;
}

export interface LeaveTypeRequest {
  name: string;
  timeCodeId: string;
  defaultAnnualDays: number | string;
  requiresApproval: boolean;
  accrualRuleJson?: string | null;
  active: boolean;
}

export interface LeaveBalance {
  id: string;
  employeeId: string;
  leaveTypeId: string;
  leaveTypeName: string | null;
  year: number;
  balanceDays: number | string;
}

export interface BalanceAdjustment {
  leaveTypeId: string;
  year: number;
  balanceDays: number | string;
}

export interface LeaveRequest {
  id: string;
  employeeId: string;
  employeeName: string | null;
  leaveTypeId: string;
  leaveTypeName: string | null;
  startDate: string;
  endDate: string;
  halfDay: boolean;
  halfDayPart: HalfDayPart | null;
  reason: string | null;
  status: LeaveRequestStatus;
  retroactive: boolean;
  approvedBy: string | null;
  approvedAt: string | null;
  rejectionReason: string | null;
  daysRequested: number | string;
  version: number;
}

export interface LeaveRequestPayload {
  employeeId: string;
  leaveTypeId: string;
  startDate: string;
  endDate: string;
  halfDay: boolean;
  halfDayPart?: HalfDayPart | null;
  reason?: string | null;
  retroactive: boolean;
}

export interface LeaveDecision {
  rejectionReason?: string | null;
}
