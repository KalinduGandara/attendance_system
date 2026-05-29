import type { PageResponse } from '../organization/types';

export interface AuditEvent {
  id: string;
  actorUserId: string | null;
  actorUsername: string;
  action: string;
  entityType: string | null;
  entityId: string | null;
  beforeJson: string | null;
  afterJson: string | null;
  ip: string | null;
  userAgent: string | null;
  requestId: string | null;
  occurredAt: string;
}

export type AuditEventPage = PageResponse<AuditEvent>;

export interface AuditSearchParams {
  actorUserId?: string;
  action?: string;
  entityType?: string;
  entityId?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export type SettingType = 'STRING' | 'NUMBER' | 'BOOLEAN' | 'JSON';

export interface SystemSetting {
  key: string;
  value: string;
  valueType: SettingType;
  description: string | null;
  updatedAt: string;
}

export type BackupTrigger = 'SCHEDULED' | 'MANUAL';
export type BackupStatus = 'RUNNING' | 'DONE' | 'FAILED';

export interface BackupJob {
  id: string;
  triggerType: BackupTrigger;
  status: BackupStatus;
  sizeBytes: number | null;
  startedAt: string | null;
  completedAt: string | null;
  errorMessage: string | null;
  downloadUrl: string | null;
  createdAt: string;
}

export interface RetentionPolicy {
  id: string;
  entityType: string;
  retainDays: number;
  enabled: boolean;
  lastRunAt: string | null;
  lastRunDeleted: number | null;
  updatedAt: string;
}

export interface UpdateRetentionPolicyRequest {
  retainDays: number;
  enabled: boolean;
}
