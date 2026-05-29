import { apiClient } from '../../lib/apiClient';
import type {
  AuditEvent,
  AuditEventPage,
  AuditSearchParams,
  BackupJob,
  RetentionPolicy,
  SystemSetting,
  UpdateRetentionPolicyRequest
} from './types';

export const adminApi = {
  // Audit log
  searchAudit: async (params: AuditSearchParams): Promise<AuditEventPage> =>
    (await apiClient.get<AuditEventPage>('/audit-events', { params })).data,
  getAuditEvent: async (id: string): Promise<AuditEvent> =>
    (await apiClient.get<AuditEvent>(`/audit-events/${id}`)).data,

  // System settings
  listSettings: async (): Promise<SystemSetting[]> =>
    (await apiClient.get<SystemSetting[]>('/system/settings')).data,
  updateSettings: async (changes: Record<string, string>): Promise<SystemSetting[]> =>
    (await apiClient.patch<SystemSetting[]>('/system/settings', changes)).data,

  // Backups
  listBackups: async (): Promise<BackupJob[]> =>
    (await apiClient.get<BackupJob[]>('/system/backups')).data,
  runBackup: async (): Promise<BackupJob> =>
    (await apiClient.post<BackupJob>('/system/backups')).data,
  getBackup: async (id: string): Promise<BackupJob> =>
    (await apiClient.get<BackupJob>(`/system/backups/${id}`)).data,
  downloadBackup: async (id: string): Promise<void> => {
    const res = await apiClient.get(`/system/backups/${id}/download`, { responseType: 'blob' });
    const blob = new Blob([res.data as BlobPart], { type: 'application/octet-stream' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `backup-${id}.sql`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  },

  // Retention policies
  listRetentionPolicies: async (): Promise<RetentionPolicy[]> =>
    (await apiClient.get<RetentionPolicy[]>('/system/retention-policies')).data,
  updateRetentionPolicy: async (
    entityType: string,
    body: UpdateRetentionPolicyRequest
  ): Promise<RetentionPolicy> =>
    (await apiClient.put<RetentionPolicy>(`/system/retention-policies/${entityType}`, body)).data
};
