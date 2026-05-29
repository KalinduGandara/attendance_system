export type ReportType =
  | 'DAILY'
  | 'DAILY_SUMMARY'
  | 'INDIVIDUAL'
  | 'INDIVIDUAL_SUMMARY'
  | 'LEAVE'
  | 'EXCEPTION'
  | 'MODIFIED_PUNCH_LOG';

export type ReportStatus = 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED' | 'CANCELLED';

export interface ReportSortSpec {
  field: string;
  dir: 'asc' | 'desc';
}

export interface ReportParameters {
  from?: string | null;
  to?: string | null;
  employeeId?: string | null;
  departmentId?: string | null;
  groupId?: string | null;
  status?: string | null;
  includeCustomFields?: string[];
  sort?: ReportSortSpec[];
}

export interface RunReportRequest {
  reportType: ReportType;
  parameters: ReportParameters;
}

export interface ReportJob {
  id: string;
  reportType: ReportType;
  status: ReportStatus;
  rowCount: number | null;
  errorMessage: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  downloadUrl: string | null;
}
