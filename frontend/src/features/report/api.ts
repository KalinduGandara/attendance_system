import { apiClient } from '../../lib/apiClient';
import type { ReportJob, RunReportRequest } from './types';

export const reportApi = {
  run: async (body: RunReportRequest): Promise<ReportJob> =>
    (await apiClient.post<ReportJob>('/reports', body)).data,
  get: async (id: string): Promise<ReportJob> =>
    (await apiClient.get<ReportJob>(`/reports/${id}`)).data,
  listRecent: async (): Promise<ReportJob[]> =>
    (await apiClient.get<ReportJob[]>('/reports')).data,
  download: async (id: string): Promise<void> => {
    const res = await apiClient.get(`/reports/${id}/download`, { responseType: 'blob' });
    const blob = new Blob([res.data as BlobPart], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `report-${id}.csv`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  }
};
